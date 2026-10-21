package com.sva.launcher;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Security checks on the shipped configuration and source: what other apps can reach, which
 * permissions are asked for, how the network is used, and APIs that must never appear.
 */
public final class SecurityChecksTest {
    private static final String A = "http://schemas.android.com/apk/res/android";
    private static final File MAIN = new File("src/main");

    private static Document xml(String path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        // No external entities or DTDs while parsing.
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new File(MAIN, path));
    }

    private static Element application() throws Exception {
        return (Element) xml("AndroidManifest.xml").getElementsByTagName("application").item(0);
    }

    @Test public void appDataIsNotBackedUpOrDebuggable() throws Exception {
        Element app = application();
        assertEquals("false", app.getAttributeNS(A, "allowBackup"));
        assertEquals("", app.getAttributeNS(A, "debuggable"));
        assertEquals("", app.getAttributeNS(A, "usesCleartextTraffic"));
        assertEquals("", app.getAttributeNS(A, "requestLegacyExternalStorage"));
    }

    @Test public void onlyExpectedComponentsAreReachableFromOtherApps() throws Exception {
        Set<String> allowed = new HashSet<>(Arrays.asList(
                ".MainActivity",                    // the launcher itself (HOME)
                ".ScreensaverSettingsActivity",     // opened by the system's screensaver settings
                ".ScreensaverService",              // bound by the system only (BIND_DREAM_SERVICE)
                ".LauncherOverrideService",         // bound by the system only (BIND_ACCESSIBILITY_SERVICE)
                ".PrayerAlertReceiver$Rearm"));     // boot and clock-change broadcasts
        Element app = application();
        for (String tag : new String[]{"activity", "service", "receiver", "provider"}) {
            NodeList nodes = app.getElementsByTagName(tag);
            for (int i = 0; i < nodes.getLength(); i++) {
                Element node = (Element) nodes.item(i);
                String name = node.getAttributeNS(A, "name");
                boolean exported = "true".equals(node.getAttributeNS(A, "exported"));
                if (exported) assertTrue(tag + " " + name + " must not be exported", allowed.contains(name));
                if (exported && tag.equals("service"))
                    assertFalse("exported service " + name + " needs a system permission",
                            node.getAttributeNS(A, "permission").isEmpty());
            }
        }
    }

    @Test public void prayerAlarmReceiverIsPrivate() throws Exception {
        NodeList receivers = application().getElementsByTagName("receiver");
        boolean found = false;
        for (int i = 0; i < receivers.getLength(); i++) {
            Element node = (Element) receivers.item(i);
            if (node.getAttributeNS(A, "name").equals(".PrayerAlertReceiver")) {
                found = true;
                assertEquals("false", node.getAttributeNS(A, "exported"));
            }
        }
        assertTrue(found);
    }

    @Test public void noSensitivePermissionsOrServices() throws Exception {
        String manifest = new String(Files.readAllBytes(new File(MAIN, "AndroidManifest.xml").toPath()), StandardCharsets.UTF_8);
        for (String forbidden : new String[]{"MANAGE_EXTERNAL_STORAGE", "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE",
                "ACCESS_COARSE_LOCATION", "ACCESS_FINE_LOCATION", "RECORD_AUDIO", "CAMERA", "READ_CONTACTS",
                "BIND_NOTIFICATION_LISTENER_SERVICE", "REQUEST_INSTALL_PACKAGES", "READ_SMS"})
            assertFalse("manifest must not use " + forbidden, manifest.contains(forbidden));
    }

    @Test public void networkIsEncryptedExceptOneKnownStream() throws Exception {
        Document config = xml("res/xml/network_security_config.xml");
        Element base = (Element) config.getElementsByTagName("base-config").item(0);
        assertEquals("false", base.getAttribute("cleartextTrafficPermitted"));
        NodeList domains = config.getElementsByTagName("domain");
        assertEquals(1, domains.getLength());
        assertEquals("m.live.net.sa", domains.item(0).getTextContent().trim());
        assertEquals("false", ((Element) domains.item(0)).getAttribute("includeSubdomains"));
        // Only the system's certificate authorities are trusted, never user-installed ones.
        NodeList anchors = config.getElementsByTagName("certificates");
        for (int i = 0; i < anchors.getLength(); i++)
            assertEquals("system", ((Element) anchors.item(i)).getAttribute("src"));
        assertEquals("@xml/network_security_config", application().getAttributeNS(A, "networkSecurityConfig"));
    }

    @Test public void accessibilityServiceSeesOnlyTheStockLaunchers() throws Exception {
        Element service = (Element) xml("res/xml/launcher_override_accessibility_service.xml").getDocumentElement();
        assertEquals("false", service.getAttributeNS(A, "canRetrieveWindowContent"));
        assertEquals("typeWindowStateChanged", service.getAttributeNS(A, "accessibilityEventTypes"));
        String packages = service.getAttributeNS(A, "packageNames");
        assertFalse(packages.isEmpty());
        for (String pkg : packages.split(","))
            assertTrue(pkg, pkg.startsWith("com.google.android.") && pkg.contains("launcher"));
    }

    @Test public void sourceAvoidsDangerousApisAndPlainHttp() throws Exception {
        Pattern http = Pattern.compile("\"http://([^\"/:]*)");
        File[] files = new File(MAIN, "java/com/sva/launcher").listFiles((dir, name) -> name.endsWith(".java"));
        assertTrue(files != null && files.length > 0);
        for (File file : files) {
            String source = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            for (String api : new String[]{"addJavascriptInterface", "setJavaScriptEnabled(true)", "MODE_WORLD_READABLE",
                    "MODE_WORLD_WRITEABLE", "Runtime.getRuntime().exec", "ProcessBuilder", "TrustManager",
                    "HostnameVerifier", "setAllowFileAccess(true)", "DexClassLoader"})
                assertFalse(file.getName() + " uses " + api, source.contains(api));
            Matcher matcher = http.matcher(source);
            while (matcher.find()) {
                String host = matcher.group(1);
                // An empty host is a scheme check such as startsWith("http://"), not a request.
                assertTrue(file.getName() + " uses plain HTTP to " + host, host.isEmpty() || host.equals("m.live.net.sa"));
            }
            if (source.contains("PendingIntent.get"))
                assertTrue(file.getName() + " PendingIntents must be immutable", source.contains("FLAG_IMMUTABLE"));
        }
    }
}
