package com.sva.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Settings for the home themes: «بسيط ١» (the original home) and «بسيط ٢» (glass cards), plus
 * the day/night look, the font, which cards show, and which tabs sit in the bottom bar.
 */
final class ModernTheme {
    static final String PREF_THEME = "homeTheme", PREF_MODE = "homeMode", PREF_FONT = "homeFont",
            PREF_TABS = "modernTabs", CARD_PREFIX = "homeCard_";
    static final int SIMPLE_1 = 1, SIMPLE_2 = 2;
    static final String[] THEME_NAMES = {"بسيط ١", "بسيط ٢"};

    static final String[] FONT_KEYS = {"noto", "tajawal", "harmattan", "jomhuria", "ruqaa"};
    static final String[] FONT_NAMES = {"نوتو", "تجوال", "هرمتان", "جمهورية", "الرقعة"};

    /** The font of the daily verse: both carry the Quranic brackets ﴿ ﴾ and marks. */
    static final String PREF_VERSE_FONT = "homeVerseFont";
    static final String[] VERSE_FONT_KEYS = {"amiri", "naskh"};
    static final String[] VERSE_FONT_NAMES = {"أميري", "نسخ"};

    /** Text size on the glass home: small, normal, large. */
    static final String PREF_TEXT_SIZE = "homeTextSize";
    static final String[] TEXT_SIZE_KEYS = {"small", "normal", "large"};
    static final String[] TEXT_SIZE_NAMES = {"صغير", "عادي", "كبير"};
    private static final float[] TEXT_SIZE_FACTORS = {0.9f, 1f, 1.1f};

    static final String[] CARD_KEYS = {"weather", "clock", "verse", "wisdom", "poetry", "occasion", "prayer"};
    static final String[] CARD_NAMES = {"الطقس", "الساعة والتاريخ", "آية اليوم", "حكمة اليوم", "أبيات شعر",
            "المناسبة القادمة", "الصلاة القادمة"};

    /** Tabs that can sit in the bottom bar of «بسيط ٢», after «آمر». */
    static final String[] TAB_KEYS = {"prayer", "events", "quran", "gallery", "haram"};
    static final String[] TAB_NAMES = {"الصلاة", "تاريخك", "القرآن الكريم", "الخلفيات", "الحرم مباشر"};

    private ModernTheme() { }

    static int theme(SharedPreferences p) { return p.getInt(PREF_THEME, SIMPLE_2) == SIMPLE_1 ? SIMPLE_1 : SIMPLE_2; }
    static boolean modern(SharedPreferences p) { return theme(p) == SIMPLE_2; }
    static boolean day(SharedPreferences p) { return "day".equals(p.getString(PREF_MODE, "night")); }

    static String font(SharedPreferences p) {
        String key = p.getString(PREF_FONT, "tajawal");
        for (String k : FONT_KEYS) if (k.equals(key)) return key;
        return "tajawal";
    }

    static String fontName(String key) {
        for (int i = 0; i < FONT_KEYS.length; i++) if (FONT_KEYS[i].equals(key)) return FONT_NAMES[i];
        return FONT_NAMES[0];
    }

    static String verseFont(SharedPreferences p) {
        String key = p.getString(PREF_VERSE_FONT, "amiri");
        return "naskh".equals(key) ? "naskh" : "amiri";
    }

    static String verseFontName(String key) { return "naskh".equals(key) ? VERSE_FONT_NAMES[1] : VERSE_FONT_NAMES[0]; }

    static int textSize(SharedPreferences p) {
        String key = p.getString(PREF_TEXT_SIZE, "normal");
        for (int i = 0; i < TEXT_SIZE_KEYS.length; i++) if (TEXT_SIZE_KEYS[i].equals(key)) return i;
        return 1;
    }

    static float textSizeFactor(SharedPreferences p) { return TEXT_SIZE_FACTORS[textSize(p)]; }

    /** Steps a string preference through a whitelist of keys. */
    static void step(SharedPreferences p, String pref, String[] keys, String current, int delta) {
        int at = 0;
        for (int i = 0; i < keys.length; i++) if (keys[i].equals(current)) at = i;
        p.edit().putString(pref, keys[Math.floorMod(at + delta, keys.length)]).apply();
    }

    /** Jomhuria has no middle dot, so separators use a bullet it does have. */
    static CharSequence forFont(String key, CharSequence text) {
        if (text == null) return "";
        if (!"jomhuria".equals(key) || text.toString().indexOf('\u00B7') < 0) return text; // Keeps colour spans.
        return text.toString().replace('\u00B7', '\u2022');
    }

    static boolean card(SharedPreferences p, String key) { return p.getBoolean(CARD_PREFIX + key, true); }

    /** Visible bar tabs in order; the default is prayer then events, as in «بسيط ١». */
    static List<String> tabs(SharedPreferences p) {
        ArrayList<String> result = new ArrayList<>();
        String saved = p.getString(PREF_TABS, "prayer,events");
        for (String key : saved.split(",")) {
            key = key.trim();
            for (String k : TAB_KEYS) if (k.equals(key) && !result.contains(key)) result.add(key);
        }
        return result;
    }

    static String tabName(String key) {
        for (int i = 0; i < TAB_KEYS.length; i++) if (TAB_KEYS[i].equals(key)) return TAB_NAMES[i];
        return key;
    }

    static String cardName(String key) {
        for (int i = 0; i < CARD_KEYS.length; i++) if (CARD_KEYS[i].equals(key)) return CARD_NAMES[i];
        return key;
    }

    // ---- Fonts (bundled under assets/fonts, SIL Open Font License) ----

    private static final Map<String, Typeface> CACHE = new HashMap<>();

    /** weight: 300 light, 400 regular, 500 medium. Missing weights fall back to the nearest file. */
    static Typeface typeface(Context context, String key, int weight) {
        String file;
        switch (key) {
            case "tajawal": file = "tajawal-" + (weight <= 300 ? 300 : weight >= 500 ? 500 : 400); break;
            case "harmattan": file = "harmattan-" + (weight >= 500 ? 500 : 400); break;
            case "jomhuria": file = "jomhuria-400"; break;
            case "ruqaa": file = "aref-ruqaa-" + (weight >= 500 ? 700 : 400); break;
            case "amiri": file = "amiri-400"; break;
            case "naskh": file = "naskh-400"; break;
            default:
                return Typeface.create(weight <= 300 ? "sans-serif-light" : weight >= 500 ? "sans-serif-medium" : "sans-serif",
                        Typeface.NORMAL);
        }
        synchronized (CACHE) {
            Typeface cached = CACHE.get(file);
            if (cached != null) return cached;
            try {
                Typeface loaded = Typeface.createFromAsset(context.getAssets(), "fonts/" + file + ".ttf");
                CACHE.put(file, loaded);
                return loaded;
            } catch (RuntimeException missing) {
                return Typeface.DEFAULT;
            }
        }
    }

    /** Jomhuria is drawn small for its size, so its text is enlarged to read like the others. */
    static float scale(String key) {
        return "jomhuria".equals(key) ? 1.45f : "harmattan".equals(key) ? 1.08f : "ruqaa".equals(key) ? 0.82f : 1f;
    }

    /**
     * Empty space above and below the letters, as a fraction of the text size, that some fonts reserve
     * (measured on the home screen's text). Trimming it keeps every font inside the same card sizes.
     */
    static float[] trim(String key) {
        if ("harmattan".equals(key)) return new float[]{0.30f, 0.27f};
        if ("jomhuria".equals(key)) return new float[]{0.02f, 0.15f};
        if ("ruqaa".equals(key)) return new float[]{0f, 0f};
        return new float[]{0f, 0f};
    }

    // ---- Daily wisdom and poetry ----

    /** Well-known Arabic proverbs. */
    static final String[] WISDOM = {
            "مَن جَدَّ وَجَد، ومَن زَرَعَ حَصَد",
            "الصبرُ مفتاحُ الفرج",
            "خيرُ الكلامِ ما قلَّ ودلَّ",
            "الوقتُ كالسيفِ إن لم تقطعْه قطعك",
            "العلمُ في الصغرِ كالنقشِ على الحجر",
            "رُبَّ أخٍ لك لم تلدْه أمُّك",
            "مَن سارَ على الدربِ وصل",
            "في التأنّي السلامة، وفي العجلةِ الندامة",
            "لكلِّ مقامٍ مقال",
            "القناعةُ كنزٌ لا يفنى",
            "الجارُ قبلَ الدار، والرفيقُ قبلَ الطريق",
            "ما حكَّ جلدَك مثلُ ظفرِك",
            "اليدُ الواحدةُ لا تصفّق",
            "إذا كان الكلامُ من فضة، فالسكوتُ من ذهب",
    };

    /** Famous lines of classical and modern Arabic poetry: {first half, second half, poet}. */
    static final String[][] POETRY = {
            {"على قدرِ أهلِ العزمِ تأتي العزائمُ", "وتأتي على قدرِ الكرامِ المكارمُ", "المتنبي"},
            {"إذا غامرتَ في شرفٍ مرومِ", "فلا تقنعْ بما دونَ النجومِ", "المتنبي"},
            {"ما كلُّ ما يتمنّى المرءُ يدركُهُ", "تجري الرياحُ بما لا تشتهي السفنُ", "المتنبي"},
            {"وإذا كانتِ النفوسُ كباراً", "تعبتْ في مرادِها الأجسامُ", "المتنبي"},
            {"دعِ الأيامَ تفعلُ ما تشاءُ", "وطِبْ نفساً إذا حكمَ القضاءُ", "الإمام الشافعي"},
            {"ولرُبَّ نازلةٍ يضيقُ لها الفتى", "ذرعاً وعندَ اللهِ منها المخرجُ", "الإمام الشافعي"},
            {"تغرّبْ عن الأوطانِ في طلبِ العلا", "وسافرْ ففي الأسفارِ خمسُ فوائدِ", "الإمام الشافعي"},
            {"ومهما تكنْ عند امرئٍ من خليقةٍ", "وإن خالَها تخفى على الناسِ تُعلَمِ", "زهير بن أبي سلمى"},
            {"ستُبدي لك الأيامُ ما كنتَ جاهلاً", "ويأتيك بالأخبارِ مَن لم تزوّدِ", "طرفة بن العبد"},
            {"وما نيلُ المطالبِ بالتمنّي", "ولكن تُؤخذُ الدنيا غِلابا", "أحمد شوقي"},
            {"وإنّما الأممُ الأخلاقُ ما بقيتْ", "فإن همُ ذهبتْ أخلاقُهم ذهبوا", "أحمد شوقي"},
            {"إذا الشعبُ يوماً أرادَ الحياةَ", "فلا بدَّ أن يستجيبَ القدرْ", "أبو القاسم الشابي"},
            {"أراكَ عصيَّ الدمعِ شيمتُكَ الصبرُ", "أما للهوى نهيٌ عليكَ ولا أمرُ", "أبو فراس الحمداني"},
            {"سيذكرُني قومي إذا جدَّ جدُّهمْ", "وفي الليلةِ الظلماءِ يُفتقدُ البدرُ", "أبو فراس الحمداني"},
            {"يا أهلَ أندلسٍ للهِ دَرُّكمُ", "ماءٌ وظلٌّ وأنهارٌ وأشجارُ", "ابن خفاجة الأندلسي"},
            {"وأحسنُ منكَ لم ترَ قطُّ عيني", "وأجملُ منكَ لم تلدِ النساءُ", "حسان بن ثابت"},
            {"خُلقتَ مبرّأً من كلِّ عيبٍ", "كأنّكَ قد خُلقتَ كما تشاءُ", "حسان بن ثابت"},
            {"لنا الدنيا ومَن أمسى عليها", "ونبطشُ حينَ نبطشُ قادرينا", "عمرو بن كلثوم"},
            {"محا البينُ ما أبقتْ عيونُ المها منّي", "فشبتُ ولم أقضِ اللُّبانةَ من سنّي", "محمود سامي البارودي"},
            {"أيُّهذا الشاكي وما بكَ داءٌ", "كُنْ جميلاً ترَ الوجودَ جميلا", "إيليا أبو ماضي"},
            {"اسلمي يا مصرُ إنّني الفدا", "ذي يدي إن مدّتِ الدنيا يدا", "مصطفى صادق الرافعي"},
            {"وُلدَ الهدى فالكائناتُ ضياءُ", "وفمُ الزمانِ تبسّمٌ وثناءُ", "أحمد شوقي"},
            {"دقّاتُ قلبِ المرءِ قائلةٌ لهُ", "إنَّ الحياةَ دقائقٌ وثوانِ", "أحمد شوقي"},
    };

    static String wisdomFor(LocalDate day) { return WISDOM[(int) Math.floorMod(day.toEpochDay(), (long) WISDOM.length)]; }

    static String[] poetryFor(LocalDate day) {
        // A different step from the proverbs, so the pairings change from day to day.
        return POETRY[(int) Math.floorMod(day.toEpochDay() * 5 + 3, (long) POETRY.length)];
    }
}
