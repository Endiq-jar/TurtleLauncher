package com.endiq.turtlelauncher.feature.ai

import android.content.Context
import com.endiq.turtlelauncher.setting.AllSettings
import java.util.Locale

/**
 * Language handling for Turtle AI.
 *
 * What this does, and what it deliberately does not:
 *
 *  - It **detects the language the user actually wrote in** (see [detect]) and resolves the
 *    launcher/device language as a fallback (see [resolve]). The chat screen asks for
 *    [replyLanguage], which prefers the message over the device.
 *  - It supplies a small **conversation shell** - the short status lines the offline rule
 *    engine answers with - in the languages below ([SHELL]). Those are one-liners, written to
 *    be reviewable by a native speaker; a wrong one is cosmetic, not a wrong diagnosis.
 *  - It hands the cloud brain an explicit instruction to answer in that language
 *    ([TurtleAiPrompt.languageInstruction]), which is what makes the long-form answers
 *    multilingual: translating all 22 topic answers by hand would be thousands of strings and
 *    would rot the moment a topic changed.
 *
 * So: offline the assistant *understands* every language it can detect - it keeps answering
 * launcher questions - but its answers are English, with a note in the user's own language
 * saying so and pointing at the setting that fixes it. With an AI key configured
 * (Settings -> Experimental) the whole answer, long-form included, comes back in the user's
 * language, built on top of the launcher's own verified answers.
 *
 * Adding a language = one entry in [SHELL] (and, if it needs stopwords for detection, one
 * entry in [LATIN_STOPWORDS]). Nothing else changes.
 */
object TurtleAiLanguage {

    /** Setting value meaning "follow the launcher/device language". */
    const val AUTO = "auto"
    const val ENGLISH = "en"

    /**
     * The short lines the offline engine needs. Long-form topic answers are not here on
     * purpose - see the class doc.
     */
    data class Shell(
        /** Opening message when a conversation starts. */
        val greeting: String,
        /** Appended when an offline (English) answer is given to a non-English speaker. */
        val englishOnly: String,
        /** No offline answer for the question. %s = the question, already shortened. */
        val unknown: String,
        /** What the user can turn on to get more. */
        val offlineHint: String,
        /** Header above web results. %s = the query. */
        val searchHeader: String,
        /** Search ran but returned nothing usable. */
        val searchNone: String,
        /** Label above the list of links. */
        val sources: String,
        /** The AI request itself failed (network, key, quota). */
        val requestFailed: String
    )

    /** Selectable languages for the settings picker: tag -> label. [AUTO] first. */
    val PICKER: List<Pair<String, String>> = listOf(
        AUTO to "Automatic (device language)",
        "en" to "English",
        "hi" to "हिन्दी (Hindi)",
        "bn" to "বাংলা (Bengali)",
        "ta" to "தமிழ் (Tamil)",
        "te" to "తెలుగు (Telugu)",
        "ml" to "മലയാളം (Malayalam)",
        "gu" to "ગુજરાતી (Gujarati)",
        "pa" to "ਪੰਜਾਬੀ (Punjabi)",
        "es" to "Español (Spanish)",
        "pt" to "Português (Portuguese)",
        "fr" to "Français (French)",
        "de" to "Deutsch (German)",
        "it" to "Italiano (Italian)",
        "pl" to "Polski (Polish)",
        "ru" to "Русский (Russian)",
        "uk" to "Українська (Ukrainian)",
        "tr" to "Türkçe (Turkish)",
        "ar" to "العربية (Arabic)",
        "fa" to "فارسی (Persian)",
        "he" to "עברית (Hebrew)",
        "zh" to "中文 (Chinese)",
        "ja" to "日本語 (Japanese)",
        "ko" to "한국어 (Korean)",
        "id" to "Bahasa Indonesia (Indonesian)",
        "vi" to "Tiếng Việt (Vietnamese)",
        "th" to "ไทย (Thai)"
    )

    private val ENGLISH_SHELL = Shell(
        greeting = "Hi! I'm Turtle AI - the launcher's built-in assistant. Ask me about " +
            "renderers, crashes, memory, mods, Java or your device. I can also search the web " +
            "and answer in your language when that's enabled in Settings -> Experimental.",
        englishOnly = "(Offline answers are English only. Add an AI key in Settings -> " +
            "Experimental to get answers in your language.)",
        unknown = "I don't have an answer for \"%s\" offline.",
        offlineHint = "I only know this launcher offline. Enable web search and/or the AI brain " +
            "in Settings -> Experimental, or type /help.",
        searchHeader = "Here's what I found online for \"%s\":",
        searchNone = "I searched but found nothing useful.",
        sources = "Sources",
        requestFailed = "I couldn't reach the AI service just now."
    )

    private val SHELL: Map<String, Shell> = mapOf(
        ENGLISH to ENGLISH_SHELL,
        "hi" to Shell(
            greeting = "नमस्ते! मैं Turtle AI हूँ - इस लॉन्चर का बिल्ट-इन असिस्टेंट। रेंडरर, क्रैश, " +
                "RAM, मॉड्स, Java या अपने डिवाइस के बारे में पूछें। Settings -> Experimental में " +
                "चालू होने पर मैं आपकी भाषा में जवाब देता हूँ और इंटरनेट भी खोज सकता हूँ।",
            englishOnly = "(ऑफ़लाइन जवाब केवल अंग्रेज़ी में हैं। अपनी भाषा में जवाब पाने के लिए " +
                "Settings -> Experimental में AI key डालें।)",
            unknown = "इसका जवाब मेरे पास ऑफ़लाइन नहीं है: \"%s\"",
            offlineHint = "ऑफ़लाइन मुझे सिर्फ़ इस लॉन्चर की जानकारी है। Settings -> Experimental में " +
                "वेब सर्च या AI चालू करें, या /help लिखें।",
            searchHeader = "\"%s\" के लिए मुझे इंटरनेट पर यह मिला:",
            searchNone = "मैंने खोजा, पर कुछ काम का नहीं मिला।",
            sources = "स्रोत",
            requestFailed = "अभी AI सेवा तक नहीं पहुँच सका।"
        ),
        "bn" to Shell(
            greeting = "নমস্কার! আমি Turtle AI — এই লঞ্চারের বিল্ট-ইন সহকারী। রেন্ডারার, ক্র্যাশ, RAM, " +
                "মড, Java বা আপনার ডিভাইস সম্পর্কে জিজ্ঞাসা করুন। Settings -> Experimental-এ " +
                "চালু থাকলে আমি আপনার ভাষায় উত্তর দিই এবং ইন্টারনেটেও খুঁজি।",
            englishOnly = "(অফলাইনে উত্তর শুধু ইংরেজিতে। নিজের ভাষায় উত্তর পেতে " +
                "Settings -> Experimental-এ AI key দিন।)",
            unknown = "এর উত্তর আমার কাছে অফলাইনে নেই: \"%s\"",
            offlineHint = "অফলাইনে আমি শুধু এই লঞ্চার সম্পর্কে জানি। Settings -> Experimental-এ " +
                "ওয়েব সার্চ বা AI চালু করুন, অথবা /help লিখুন।",
            searchHeader = "\"%s\"-এর জন্য ইন্টারনেটে যা পেলাম:",
            searchNone = "খুঁজেছি, কিন্তু কাজের কিছু পাইনি।",
            sources = "সূত্র",
            requestFailed = "এখনই AI পরিষেবায় পৌঁছাতে পারিনি।"
        ),
        "ta" to Shell(
            greeting = "வணக்கம்! நான் Turtle AI - இந்த லாஞ்சரின் உள்ளமைந்த உதவியாளர். ரெண்டரர்கள், " +
                "கிராஷ், RAM, மோட்கள், Java அல்லது உங்கள் சாதனம் பற்றி கேளுங்கள். " +
                "Settings -> Experimental-இல் இயக்கினால் உங்கள் மொழியில் பதிலளிக்கிறேன்.",
            englishOnly = "(ஆஃப்லைனில் பதில்கள் ஆங்கிலத்தில் மட்டுமே. உங்கள் மொழியில் பதில் பெற " +
                "Settings -> Experimental-இல் AI key சேர்க்கவும்.)",
            unknown = "இதற்கான பதில் என்னிடம் ஆஃப்லைனில் இல்லை: \"%s\"",
            offlineHint = "ஆஃப்லைனில் எனக்கு இந்த லாஞ்சர் பற்றி மட்டுமே தெரியும். " +
                "Settings -> Experimental-இல் இணைய தேடல் அல்லது AI-ஐ இயக்கவும்.",
            searchHeader = "\"%s\" க்காக இணையத்தில் கிடைத்தவை:",
            searchNone = "தேடினேன், பயனுள்ள எதுவும் கிடைக்கவில்லை.",
            sources = "மூலங்கள்",
            requestFailed = "இப்போது AI சேவையை அணுக முடியவில்லை."
        ),
        "es" to Shell(
            greeting = "¡Hola! Soy Turtle AI, el asistente integrado del launcher. Pregúntame por " +
                "renderizadores, cierres, RAM, mods, Java o tu dispositivo. También puedo buscar en " +
                "internet y responder en tu idioma si lo activas en Settings -> Experimental.",
            englishOnly = "(Las respuestas sin conexión solo están en inglés. Añade una clave de IA " +
                "en Settings -> Experimental para recibirlas en tu idioma.)",
            unknown = "No tengo respuesta para \"%s\" sin conexión.",
            offlineHint = "Sin conexión solo conozco este launcher. Activa la búsqueda web o la IA " +
                "en Settings -> Experimental, o escribe /help.",
            searchHeader = "Esto es lo que encontré en internet sobre \"%s\":",
            searchNone = "Busqué pero no encontré nada útil.",
            sources = "Fuentes",
            requestFailed = "No pude conectar con el servicio de IA ahora mismo."
        ),
        "pt" to Shell(
            greeting = "Olá! Sou o Turtle AI, o assistente integrado do launcher. Pergunte sobre " +
                "renderizadores, travamentos, RAM, mods, Java ou seu aparelho. Também posso " +
                "pesquisar na internet e responder no seu idioma se isso estiver ativado em " +
                "Settings -> Experimental.",
            englishOnly = "(As respostas offline são apenas em inglês. Adicione uma chave de IA em " +
                "Settings -> Experimental para recebê-las no seu idioma.)",
            unknown = "Não tenho resposta para \"%s\" offline.",
            offlineHint = "Offline eu só conheço este launcher. Ative a pesquisa na web ou a IA em " +
                "Settings -> Experimental, ou digite /help.",
            searchHeader = "Isto foi o que encontrei na internet sobre \"%s\":",
            searchNone = "Pesquisei, mas não encontrei nada útil.",
            sources = "Fontes",
            requestFailed = "Não consegui falar com o serviço de IA agora."
        ),
        "fr" to Shell(
            greeting = "Salut ! Je suis Turtle AI, l'assistant intégré du launcher. Posez-moi des " +
                "questions sur les renderers, les crashs, la RAM, les mods, Java ou votre appareil. " +
                "Je peux aussi chercher sur internet et répondre dans votre langue si c'est activé " +
                "dans Settings -> Experimental.",
            englishOnly = "(Les réponses hors ligne sont en anglais uniquement. Ajoutez une clé IA " +
                "dans Settings -> Experimental pour les obtenir dans votre langue.)",
            unknown = "Je n'ai pas de réponse hors ligne pour « %s ».",
            offlineHint = "Hors ligne, je ne connais que ce launcher. Activez la recherche web ou " +
                "l'IA dans Settings -> Experimental, ou tapez /help.",
            searchHeader = "Voici ce que j'ai trouvé en ligne pour « %s » :",
            searchNone = "J'ai cherché mais je n'ai rien trouvé d'utile.",
            sources = "Sources",
            requestFailed = "Je n'ai pas pu joindre le service d'IA pour le moment."
        ),
        "de" to Shell(
            greeting = "Hallo! Ich bin Turtle AI, der eingebaute Assistent des Launchers. Frag mich " +
                "zu Renderern, Abstürzen, RAM, Mods, Java oder deinem Gerät. Ich kann auch im " +
                "Internet suchen und in deiner Sprache antworten, wenn du das in " +
                "Settings -> Experimental aktivierst.",
            englishOnly = "(Offline-Antworten gibt es nur auf Englisch. Hinterlege einen KI-Schlüssel " +
                "in Settings -> Experimental, um Antworten in deiner Sprache zu bekommen.)",
            unknown = "Dafür habe ich offline keine Antwort: \"%s\"",
            offlineHint = "Offline kenne ich nur diesen Launcher. Aktiviere Websuche oder die KI in " +
                "Settings -> Experimental, oder tippe /help.",
            searchHeader = "Das habe ich im Internet zu \"%s\" gefunden:",
            searchNone = "Ich habe gesucht, aber nichts Nützliches gefunden.",
            sources = "Quellen",
            requestFailed = "Ich konnte den KI-Dienst gerade nicht erreichen."
        ),
        "it" to Shell(
            greeting = "Ciao! Sono Turtle AI, l'assistente integrato del launcher. Chiedimi di " +
                "renderer, crash, RAM, mod, Java o del tuo dispositivo. Posso anche cercare su " +
                "internet e rispondere nella tua lingua se lo attivi in Settings -> Experimental.",
            englishOnly = "(Le risposte offline sono solo in inglese. Aggiungi una chiave AI in " +
                "Settings -> Experimental per averle nella tua lingua.)",
            unknown = "Non ho una risposta offline per \"%s\".",
            offlineHint = "Offline conosco solo questo launcher. Attiva la ricerca web o l'AI in " +
                "Settings -> Experimental, oppure scrivi /help.",
            searchHeader = "Ecco cosa ho trovato online per \"%s\":",
            searchNone = "Ho cercato ma non ho trovato nulla di utile.",
            sources = "Fonti",
            requestFailed = "Non riesco a contattare il servizio AI adesso."
        ),
        "pl" to Shell(
            greeting = "Cześć! Jestem Turtle AI, wbudowany asystent launchera. Pytaj o renderery, " +
                "crashe, RAM, mody, Java lub swoje urządzenie. Mogę też szukać w internecie i " +
                "odpowiadać w Twoim języku, jeśli włączysz to w Settings -> Experimental.",
            englishOnly = "(Odpowiedzi offline są tylko po angielsku. Dodaj klucz AI w " +
                "Settings -> Experimental, aby dostawać je w swoim języku.)",
            unknown = "Offline nie mam odpowiedzi na \"%s\".",
            offlineHint = "Offline znam tylko ten launcher. Włącz wyszukiwanie w sieci lub AI w " +
                "Settings -> Experimental albo wpisz /help.",
            searchHeader = "Oto co znalazłem w internecie dla \"%s\":",
            searchNone = "Szukałem, ale nie znalazłem nic przydatnego.",
            sources = "Źródła",
            requestFailed = "Nie udało się teraz połączyć z usługą AI."
        ),
        "ru" to Shell(
            greeting = "Привет! Я Turtle AI — встроенный помощник лаунчера. Спроси про рендереры, " +
                "вылеты, RAM, моды, Java или своё устройство. Я также умею искать в интернете и " +
                "отвечаю на твоём языке, если это включено в Settings -> Experimental.",
            englishOnly = "(Офлайн-ответы только на английском. Добавь ключ ИИ в " +
                "Settings -> Experimental, чтобы получать ответы на своём языке.)",
            unknown = "У меня нет офлайн-ответа на «%s».",
            offlineHint = "Офлайн я знаю только этот лаунчер. Включи веб-поиск или ИИ в " +
                "Settings -> Experimental либо напиши /help.",
            searchHeader = "Вот что я нашёл в интернете по запросу «%s»:",
            searchNone = "Я искал, но ничего полезного не нашёл.",
            sources = "Источники",
            requestFailed = "Не удалось связаться с сервисом ИИ."
        ),
        "uk" to Shell(
            greeting = "Привіт! Я Turtle AI — вбудований помічник лаунчера. Запитай про рендерери, " +
                "вильоти, RAM, моди, Java або свій пристрій. Я також вмію шукати в інтернеті й " +
                "відповідаю твоєю мовою, якщо це увімкнено в Settings -> Experimental.",
            englishOnly = "(Офлайн-відповіді лише англійською. Додай ключ ШІ в " +
                "Settings -> Experimental, щоб отримувати їх своєю мовою.)",
            unknown = "У мене немає офлайн-відповіді на «%s».",
            offlineHint = "Офлайн я знаю лише цей лаунчер. Увімкни веб-пошук або ШІ в " +
                "Settings -> Experimental чи напиши /help.",
            searchHeader = "Ось що я знайшов в інтернеті за запитом «%s»:",
            searchNone = "Я шукав, але нічого корисного не знайшов.",
            sources = "Джерела",
            requestFailed = "Не вдалося зв'язатися із сервісом ШІ."
        ),
        "tr" to Shell(
            greeting = "Merhaba! Ben Turtle AI, başlatıcının dahili asistanı. Renderer'lar, çökmeler, " +
                "RAM, modlar, Java veya cihazın hakkında sorabilirsin. Settings -> Experimental'da " +
                "açarsan internette arayabilir ve kendi dilinde yanıtlayabilirim.",
            englishOnly = "(Çevrimdışı yanıtlar yalnızca İngilizcedir. Kendi dilinde yanıt için " +
                "Settings -> Experimental'da AI anahtarı ekle.)",
            unknown = "Çevrimdışı olarak \"%s\" için yanıtım yok.",
            offlineHint = "Çevrimdışıyken yalnızca bu başlatıcıyı bilirim. " +
                "Settings -> Experimental'da web aramasını veya AI'yı aç ya da /help yaz.",
            searchHeader = "\"%s\" için internette bulduklarım:",
            searchNone = "Aradım ama işe yarar bir şey bulamadım.",
            sources = "Kaynaklar",
            requestFailed = "Şu anda AI servisine ulaşamadım."
        ),
        "ar" to Shell(
            greeting = "مرحبًا! أنا Turtle AI، المساعد المدمج في اللانشر. اسألني عن المُصيّرات " +
                "(renderers)، الأعطال، الذاكرة، المودات، Java أو جهازك. أستطيع أيضًا البحث في " +
                "الإنترنت والرد بلغتك عند تفعيل ذلك في Settings -> Experimental.",
            englishOnly = "(الأجوبة دون اتصال بالإنجليزية فقط. أضف مفتاح AI في " +
                "Settings -> Experimental للحصول على الأجوبة بلغتك.)",
            unknown = "ليس لدي جواب دون اتصال عن: \"%s\"",
            offlineHint = "دون اتصال لا أعرف إلا هذا اللانشر. فعّل البحث في الويب أو الـAI من " +
                "Settings -> Experimental، أو اكتب /help.",
            searchHeader = "هذا ما وجدته في الإنترنت عن \"%s\":",
            searchNone = "بحثت ولم أجد شيئًا مفيدًا.",
            sources = "المصادر",
            requestFailed = "لم أتمكن من الوصول إلى خدمة الـAI الآن."
        ),
        "fa" to Shell(
            greeting = "سلام! من Turtle AI هستم، دستیار داخلی لانچر. درباره رندررها، کرش، رم، مادها، " +
                "Java یا دستگاهت بپرس. اگر در Settings -> Experimental فعال کنی، در اینترنت هم " +
                "جستوجو میکنم و به زبان خودت پاسخ میدهم.",
            englishOnly = "(پاسخهای آفلاین فقط انگلیسی است. برای پاسخ به زبان خودت کلید AI را در " +
                "Settings -> Experimental وارد کن.)",
            unknown = "برای «%s» پاسخ آفلاین ندارم.",
            offlineHint = "آفلاین فقط این لانچر را میشناسم. جستوجوی وب یا AI را در " +
                "Settings -> Experimental فعال کن یا /help بنویس.",
            searchHeader = "این چیزی است که در اینترنت برای «%s» یافتم:",
            searchNone = "جستوجو کردم اما چیز مفیدی پیدا نشد.",
            sources = "منابع",
            requestFailed = "الان نتوانستم به سرویس AI وصل شوم."
        ),
        "he" to Shell(
            greeting = "היי! אני Turtle AI, העוזר המובנה של הלאנצ'ר. שאל אותי על רנדררים, קריסות, " +
                "RAM, מודים, Java או המכשיר שלך. אני יכול גם לחפש באינטרנט ולענות בשפה שלך אם זה " +
                "מופעל ב-Settings -> Experimental.",
            englishOnly = "(תשובות לא מקוונות הן באנגלית בלבד. הוסף מפתח AI ב-Settings -> " +
                "Experimental כדי לקבל תשובות בשפה שלך.)",
            unknown = "אין לי תשובה לא מקוונת עבור \"%s\".",
            offlineHint = "באופן לא מקוון אני מכיר רק את הלאנצ'ר הזה. הפעל חיפוש אינטרנט או AI " +
                "ב-Settings -> Experimental, או הקלד /help.",
            searchHeader = "הנה מה שמצאתי באינטרנט עבור \"%s\":",
            searchNone = "חיפשתי אבל לא מצאתי משהו שימושי.",
            sources = "מקורות",
            requestFailed = "לא הצלחתי להגיע לשירות ה-AI כרגע."
        ),
        "zh" to Shell(
            greeting = "你好！我是 Turtle AI，启动器内置的助手。可以问我渲染器、崩溃、内存、模组、" +
                "Java 或你的设备。在 Settings -> Experimental 中开启后，我还能联网搜索并用你的语言回答。",
            englishOnly = "（离线回答只有英文。在 Settings -> Experimental 中填入 AI 密钥即可用你的语言回答。）",
            unknown = "离线状态下我无法回答“%s”。",
            offlineHint = "离线时我只了解这个启动器。请在 Settings -> Experimental 中开启网页搜索或 AI，或输入 /help。",
            searchHeader = "关于“%s”，我在网上找到：",
            searchNone = "我搜索了，但没找到有用的内容。",
            sources = "来源",
            requestFailed = "目前无法连接 AI 服务。"
        ),
        "ja" to Shell(
            greeting = "こんにちは！Turtle AI です。ランチャー内蔵のアシスタントで、レンダラー、" +
                "クラッシュ、RAM、Mod、Java、端末について聞けます。Settings -> Experimental で有効にすると、" +
                "Web 検索とあなたの言語での回答もできます。",
            englishOnly = "（オフラインの回答は英語のみです。Settings -> Experimental で AI キーを設定すると、" +
                "あなたの言語で回答します。）",
            unknown = "「%s」についてはオフラインでは答えられません。",
            offlineHint = "オフラインではこのランチャーのことしか分かりません。Settings -> Experimental で " +
                "Web 検索か AI を有効にするか、/help と入力してください。",
            searchHeader = "「%s」について Web で見つけた内容:",
            searchNone = "検索しましたが、役立つ情報は見つかりませんでした。",
            sources = "出典",
            requestFailed = "今は AI サービスに接続できませんでした。"
        ),
        "ko" to Shell(
            greeting = "안녕하세요! 런처에 내장된 Turtle AI입니다. 렌더러, 크래시, RAM, 모드, Java, " +
                "기기 설정을 물어보세요. Settings -> Experimental에서 켜면 웹 검색과 한국어 답변도 가능합니다.",
            englishOnly = "(오프라인 답변은 영어만 지원합니다. Settings -> Experimental에서 AI 키를 넣으면 " +
                "한국어로 답합니다.)",
            unknown = "오프라인에서는 \"%s\"에 답할 수 없습니다.",
            offlineHint = "오프라인에서는 이 런처 정보만 알고 있습니다. Settings -> Experimental에서 웹 검색이나 " +
                "AI를 켜거나 /help를 입력하세요.",
            searchHeader = "\"%s\"에 대해 인터넷에서 찾은 내용:",
            searchNone = "검색했지만 유용한 내용을 찾지 못했습니다.",
            sources = "출처",
            requestFailed = "지금은 AI 서비스에 연결하지 못했습니다."
        ),
        "id" to Shell(
            greeting = "Hai! Saya Turtle AI, asisten bawaan launcher ini. Tanyakan soal renderer, " +
                "crash, RAM, mod, Java, atau perangkatmu. Saya juga bisa mencari di internet dan " +
                "menjawab dalam bahasamu jika diaktifkan di Settings -> Experimental.",
            englishOnly = "(Jawaban offline hanya dalam bahasa Inggris. Tambahkan kunci AI di " +
                "Settings -> Experimental untuk jawaban dalam bahasamu.)",
            unknown = "Saya tidak punya jawaban offline untuk \"%s\".",
            offlineHint = "Secara offline saya hanya tahu launcher ini. Aktifkan pencarian web atau " +
                "AI di Settings -> Experimental, atau ketik /help.",
            searchHeader = "Ini yang saya temukan di internet untuk \"%s\":",
            searchNone = "Saya sudah mencari, tapi tidak menemukan yang berguna.",
            sources = "Sumber",
            requestFailed = "Saya tidak bisa menghubungi layanan AI saat ini."
        ),
        "vi" to Shell(
            greeting = "Xin chào! Tôi là Turtle AI, trợ lý có sẵn trong launcher. Hỏi tôi về renderer, " +
                "lỗi crash, RAM, mod, Java hoặc thiết bị của bạn. Tôi cũng có thể tìm trên internet " +
                "và trả lời bằng ngôn ngữ của bạn nếu bật trong Settings -> Experimental.",
            englishOnly = "(Câu trả lời ngoại tuyến chỉ có tiếng Anh. Thêm khoá AI trong " +
                "Settings -> Experimental để nhận câu trả lời bằng ngôn ngữ của bạn.)",
            unknown = "Ngoại tuyến tôi không có câu trả lời cho \"%s\".",
            offlineHint = "Ngoại tuyến tôi chỉ biết về launcher này. Hãy bật tìm kiếm web hoặc AI " +
                "trong Settings -> Experimental, hoặc gõ /help.",
            searchHeader = "Đây là những gì tôi tìm được trên internet cho \"%s\":",
            searchNone = "Tôi đã tìm nhưng không thấy gì hữu ích.",
            sources = "Nguồn",
            requestFailed = "Hiện không kết nối được dịch vụ AI."
        ),
        "th" to Shell(
            greeting = "สวัสดี! ฉันคือ Turtle AI ผู้ช่วยที่มาพร้อมกับตัว launcer นี้ ถามเรื่อง " +
                "renderer, การแครช, RAM, มอด, Java หรืออุปกรณ์ของคุณได้ ถ้าเปิดใน " +
                "Settings -> Experimental ฉันค้นเว็บและตอบเป็นภาษาของคุณได้ด้วย",
            englishOnly = "(คำตอบแบบออฟไลน์มีแค่ภาษาอังกฤษเท่านั้น ใส่คีย์ AI ใน " +
                "Settings -> Experimental เพื่อให้ตอบเป็นภาษาของคุณ)",
            unknown = "ออฟไลน์ฉันไม่มีคำตอบสำหรับ \"%s\"",
            offlineHint = "ออฟไลน์ฉันรู้แค่เรื่อง launcer นี้ เปิดการค้นหาเว็บหรือ AI ใน " +
                "Settings -> Experimental หรือพิมพ์ /help",
            searchHeader = "นี่คือสิ่งที่ฉันพบในอินเทอร์เน็ตสำหรับ \"%s\":",
            searchNone = "ฉันค้นแล้วแต่ไม่พบอะไรที่มีประโยชน์",
            sources = "แหล่งอ้างอิง",
            requestFailed = "ตอนนี้เชื่อมต่อบริการ AI ไม่ได้"
        )
    )

    /**
     * Distinctive function words per Latin-script language, used only when the text has no
     * other script to identify. Deliberately small: two or more matches are required, so a
     * false positive takes a coincidence. Misses are harmless - detection falls back to the
     * device language, and the cloud brain mirrors whatever the user writes regardless.
     */
    private val LATIN_STOPWORDS: Map<String, List<String>> = mapOf(
        "es" to listOf("que", "como", "cómo", "para", "está", "esta", "pero", "muy", "juego", "porque", "tengo"),
        "pt" to listOf("não", "que", "uma", "para", "está", "meu", "jogo", "mas", "você", "porque", "tenho"),
        "fr" to listOf("je", "pas", "pour", "avec", "est", "mon", "mais", "vous", "comment", "pourquoi", "j'ai"),
        "de" to listOf("ich", "nicht", "und", "ist", "das", "wie", "mein", "aber", "mit", "für", "warum"),
        "it" to listOf("non", "che", "per", "con", "sono", "mio", "come", "perché", "questo", "gioco", "molto"),
        "pl" to listOf("nie", "jest", "jak", "dla", "się", "moje", "ale", "czy", "dlaczego", "gra", "bardzo"),
        "id" to listOf("saya", "tidak", "dan", "yang", "untuk", "dengan", "kenapa", "mengapa", "ini", "sangat"),
        "tr" to listOf("bir", "için", "nasıl", "değil", "benim", "ama", "ile", "neden", "oyun", "çok"),
        "vi" to listOf("tôi", "không", "và", "của", "cho", "tại", "sao", "như", "này", "game"),
        "en" to listOf("the", "why", "how", "my", "game", "not", "and", "with", "for", "crash", "does")
    )

    /** Script ranges, most specific first. A hit is proof of the language family. */
    private val SCRIPT_RANGES: List<Pair<CharRange, String>> = listOf(
        0x0900.toChar()..0x097F.toChar() to "hi",   // Devanagari
        0x0980.toChar()..0x09FF.toChar() to "bn",   // Bengali
        0x0A00.toChar()..0x0A7F.toChar() to "pa",   // Gurmukhi
        0x0A80.toChar()..0x0AFF.toChar() to "gu",   // Gujarati
        0x0B00.toChar()..0x0B7F.toChar() to "or",   // Odia
        0x0B80.toChar()..0x0BFF.toChar() to "ta",   // Tamil
        0x0C00.toChar()..0x0C7F.toChar() to "te",   // Telugu
        0x0C80.toChar()..0x0CFF.toChar() to "kn",   // Kannada
        0x0D00.toChar()..0x0D7F.toChar() to "ml",   // Malayalam
        0x0D80.toChar()..0x0DFF.toChar() to "si",   // Sinhala
        0x0E00.toChar()..0x0E7F.toChar() to "th",   // Thai
        0x0E80.toChar()..0x0EFF.toChar() to "lo",   // Lao
        0x1000.toChar()..0x109F.toChar() to "my",   // Myanmar
        0x10A0.toChar()..0x10FF.toChar() to "ka",   // Georgian
        0x0530.toChar()..0x058F.toChar() to "hy",   // Armenian
        0x1200.toChar()..0x137F.toChar() to "am",   // Ethiopic
        0x0370.toChar()..0x03FF.toChar() to "el",   // Greek
        0x0590.toChar()..0x05FF.toChar() to "he",   // Hebrew
        0x0600.toChar()..0x06FF.toChar() to "ar",   // Arabic (Persian refined below)
        0x0400.toChar()..0x04FF.toChar() to "ru",   // Cyrillic (Ukrainian refined below)
        0x3040.toChar()..0x30FF.toChar() to "ja",   // Kana - checked before Han
        0xAC00.toChar()..0xD7AF.toChar() to "ko",   // Hangul
        0x4E00.toChar()..0x9FFF.toChar() to "zh"    // Han
    )

    /** Persian-only letters, to tell Persian from Arabic inside the same Unicode block. */
    private val PERSIAN_MARKERS = charArrayOf('\u067E', '\u0686', '\u0698', '\u06AF', '\u06CC')
    /** Ukrainian-only letters, to tell Ukrainian from Russian. */
    private val UKRAINIAN_MARKERS = charArrayOf('\u0456', '\u0457', '\u0454', '\u0491')

    /**
     * @return a BCP-47 language tag for the text, or null when it can't be told apart
     * (short English tech questions like "fps?" legitimately return null).
     */
    @JvmStatic
    fun detect(text: String): String? {
        if (text.isBlank()) return null

        // 1. Writing system: unambiguous when it hits.
        val scriptHits = SCRIPT_RANGES.map { (range, tag) -> tag to text.count { it in range } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
        if (scriptHits.isNotEmpty()) {
            val tag = scriptHits[0].first
            return when (tag) {
                "ar" -> if (PERSIAN_MARKERS.any { text.contains(it) }) "fa" else "ar"
                "ru" -> if (UKRAINIAN_MARKERS.any { text.contains(it) }) "uk" else "ru"
                "zh" -> if (text.any { it in 0x3040.toChar()..0x30FF.toChar() }) "ja" else "zh"
                else -> tag
            }
        }

        // 2. Latin script: score the function words. Needs 2 matches so one shared word
        //    ("no", "game") can't decide it.
        val lower = text.lowercase(Locale.ROOT)
        val best = LATIN_STOPWORDS.map { (tag, words) ->
            tag to words.count { word -> Regex("(?<![\\p{L}])" + Regex.escape(word) + "(?![\\p{L}])").containsMatchIn(lower) }
        }.maxByOrNull { it.second }
        return if (best != null && best.second >= 2) best.first else null
    }

    /** The language the launcher itself is running in, as a bare tag ("en", "hi", ...). */
    @JvmStatic
    fun deviceLanguage(context: Context): String = runCatching {
        val locales = context.resources.configuration.locales
        val tag = if (locales.isEmpty) Locale.getDefault().language else locales[0].language
        tag.ifBlank { ENGLISH }
    }.getOrDefault(runCatching { Locale.getDefault().language }.getOrDefault(ENGLISH))
        .ifBlank { ENGLISH }
        .lowercase(Locale.ROOT)

    /**
     * The process/system language. Same answer as [deviceLanguage] for anything with a Context,
     * and the only honest answer for the crash-reporting path, which runs from the game JVM's
     * exit hook and has no Context (see AiCrashAdvisor).
     */
    @JvmStatic
    fun systemLanguage(): String =
        runCatching { Locale.getDefault().language }.getOrDefault(ENGLISH)
            .ifBlank { ENGLISH }
            .lowercase(Locale.ROOT)

    /**
     * The language to *answer* in: the launcher language unless the user overrode it in
     * Settings -> Experimental -> Turtle AI language.
     */
    @JvmStatic
    fun resolve(context: Context): String {
        val setting = runCatching { AllSettings.aiLanguage.getValue() }.getOrDefault(AUTO)
            .trim().lowercase(Locale.ROOT)
        return if (setting.isNotEmpty() && setting != AUTO) setting else deviceLanguage(context)
    }

    /**
     * The language a specific message should be answered in: what the user wrote in, if we
     * can tell; otherwise [resolve]. This is what makes the assistant follow the language of
     * the conversation rather than only the language of the phone.
     */
    @JvmStatic
    fun replyLanguage(context: Context, input: String): String =
        detect(input) ?: resolve(context)

    @JvmStatic
    fun isEnglish(tag: String): Boolean = base(tag) == ENGLISH

    /** Shell strings for [tag], falling back to English for a language we don't ship lines for. */
    @JvmStatic
    fun shell(tag: String): Shell = SHELL[base(tag)] ?: ENGLISH_SHELL

    /** Human-readable name for a tag, for messages and the picker. */
    @JvmStatic
    fun displayName(tag: String): String {
        val bare = base(tag)
        PICKER.firstOrNull { it.first == bare }?.let { return it.second }
        return runCatching { Locale.forLanguageTag(bare).displayLanguage }.getOrDefault("")
            .ifBlank { bare }
    }

    /** "हिन्दी (Hindi)" -> "हिन्दी" - the label without the English gloss. */
    @JvmStatic
    fun nativeName(tag: String): String = displayName(tag).substringBefore(" (")

    /** Everything the picker offers, [AUTO] included, as (tag, label). */
    @JvmStatic
    fun pickerEntries(): List<Pair<String, String>> = PICKER

    private fun base(tag: String): String =
        tag.trim().lowercase(Locale.ROOT).substringBefore('-').substringBefore('_').ifBlank { ENGLISH }
}
