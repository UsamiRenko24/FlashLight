package com.name.flashlight.integration.language;


import com.name.flashlight.R;

import java.util.Locale;

public enum Language {
//    DEFAULT(new Locale(""), R.string.string_language_default),  // 系统默认
    ENGLISH(new Locale("en"), R.string.string_language_english),          // 英语
    GERMAN(new Locale("de"), R.string.string_language_german),           // 德语
    GREEK(new Locale("el"), R.string.string_language_greek),           // 希腊语
    SPANISH(new Locale("es"), R.string.string_language_spanish),          // 西班牙语
    FRENCH(new Locale("fr"), R.string.string_language_french),          // 法语
    HINDI(new Locale("hi"), R.string.string_language_hindi),                // 印地语
    HUNGARIAN(new Locale("hu"), R.string.string_language_hungarian),         // 匈牙利语
    ITALIAN(new Locale("it"), R.string.string_language_italian),         // 意大利语
    JAPANESE(new Locale("ja"), R.string.string_language_japanese),           // 日语
    KOREAN(new Locale("ko"), R.string.string_language_korean),             // 韩语
    DUTCH(new Locale("nl"), R.string.string_language_dutch),         // 荷兰语
    POLISH(new Locale("pl"), R.string.string_language_polish),            // 波兰语
    PORTUGUESE(new Locale("pt"), R.string.string_language_portuguese),     // 葡萄牙语
    RUSSIAN(new Locale("ru"), R.string.string_language_russian),          // 俄语
    SWEDISH(new Locale("sv"), R.string.string_language_swedish),          // 瑞典语
    THAI(new Locale("th"), R.string.string_language_thai),              // 泰语
    TURKISH(new Locale("tr"), R.string.string_language_turkish),           // 土耳其语
    VIETNAMESE(new Locale("vi"), R.string.string_language_vietnamese),    // 越南语
    TRADITIONAL(new Locale("zh"), R.string.string_language_traditional_chinese);     // 中文繁体

    private final Locale locale;
    private final int stringRes;

    Language(Locale locale, int stringRes) {
        this.locale = locale;
        this.stringRes = stringRes;
    }

    public Locale getLocale() {
        return locale;
    }

    public int getStringRes() {
        return stringRes;
    }

}

