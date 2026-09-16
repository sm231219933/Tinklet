package com.tinklet.bharatdatingapp.utils

object Translator {
    private val translations = mapOf(
        "ja" to mapOf(
            "Tinklet" to "ティンクレット",
            "Discovery" to "発見",
            "Inbox" to "受信トレイ",
            "Matches" to "マッチング",
            "Chats" to "チャット",
            "Me" to "マイページ",
            "Search Filters" to "検索フィルター",
            "No more profiles!" to "プロフィールはもうありません！",
            "Viewed Profile" to "閲覧済みプロフィール",
            "Edit Profile Details" to "プロフィール詳細を編集",
            "My Photos" to "私の写真",
            "Notifications" to "通知",
            "Interaction Sounds" to "インタラクションサウンド",
            "Theme" to "テーマ",
            "Language" to "言語",
            "Support & Help" to "サポートとヘルプ",
            "Privacy Policy" to "プライバシーポリシー",
            "Danger Zone" to "デンジャーゾーン",
            "Delete Profile" to "プロフィールを削除",
            "Logout" to "ログアウト",
            "Boost Profile" to "プロフィールをブースト",
            "Coins" to "コイン",
            "Status" to "ステータス",
            "Premium" to "プレミアム",
            "Free" to "フリー",
            "Age" to "年齢",
            "Location Settings" to "場所の設定",
            "Country" to "国",
            "Search State" to "州を検索",
            "Categorical Preferences" to "カテゴリーの好み",
            "Gender" to "性別",
            "Religion" to "宗教",
            "Habits" to "習慣",
            "Intentions" to "意図",
            "Apply" to "適用",
            "Cancel" to "キャンセル",
            "TESTING PROFILE" to "テストプロフィール",
            "Upload Photos" to "写真をアップロード",
            "No photos uploaded yet." to "まだ写真がアップロードされていません。"
        ),
        "hi" to mapOf(
            "Tinklet" to "टिंकलेट",
            "Discovery" to "खोजें",
            "Inbox" to "इनबॉक्स",
            "Matches" to "मैच",
            "Chats" to "चैट",
            "Me" to "मैं",
            "Search Filters" to "खोज फ़िल्टर",
            "No more profiles!" to "और प्रोफाइल नहीं हैं!",
            "Viewed Profile" to "देखी गई प्रोफाइल",
            "Edit Profile Details" to "प्रोफ़ाइल विवरण संपादित करें",
            "My Photos" to "मेरी तस्वीरें",
            "Notifications" to "सूचनाएं",
            "Interaction Sounds" to "इंटरैक्शन ध्वनियाँ",
            "Theme" to "थीम",
            "Language" to "भाषा",
            "Support & Help" to "सहायता और मदद",
            "Privacy Policy" to "गोपनीयता नीति",
            "Danger Zone" to "खतरनाक क्षेत्र",
            "Delete Profile" to "प्रोफ़ाइल हटाएं",
            "Logout" to "लॉग आउट",
            "Boost Profile" to "प्रोफ़ाइल बूस्ट करें",
            "Coins" to "सिक्के",
            "Status" to "स्थिति"
        )
        // Add more languages as needed...
    )

    fun translate(text: String, langCode: String): String {
        return translations[langCode]?.get(text) ?: text
    }
}
