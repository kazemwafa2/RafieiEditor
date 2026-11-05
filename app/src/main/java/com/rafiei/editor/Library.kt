package com.rafiei.editor

object Library {
    class Cat(val id: String, val fa: String, val en: String, val items: List<String>) {
        fun name(): String = L(fa, en)
    }

    private fun split(s: String): List<String> = s.trim().split(" ").filter { it.isNotEmpty() }

    val EMOJI = listOf(
        Cat("smile", "لبخند", "Smileys", split("😀 😃 😄 😁 😆 😅 😂 🤣 😊 😇 🙂 🙃 😉 😌 😍 🥰 😘 😗 😙 😚 😋 😛 😝 😜 🤪 🤨 🧐 🤓 😎 🤩 🥳 😏 😒 😞 😔 😟 😕 🙁 ☹️ 😣 😖 😫 😩 🥺 😢 😭 😤 😠 😡 🤬 🤯 😳 🥵 🥶 😱 😨 😰 😥 😓 🤗 🤔 🤭 🤫 🤥 😶 😐 😑 😬 🙄 😯 😦 😧 😮 😲 🥱 😴 🤤 😪 😵 🤐 🥴 🤢 🤮 🤧 😷 🤒 🤕")),
        Cat("heart", "قلب", "Hearts", split("❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💟 ♥️ 😍 🥰 😘 💋 💌 💐 🌹 🥀 🌷 🌸 💍 💑 💏 👩‍❤️‍👨 🫶 🫰 🤟 🤞 👌 👍 👏 🙌 🙏")),
        Cat("people", "افراد", "People", split("👋 🤚 🖐️ ✋ 🖖 👌 🤏 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ 👍 👎 ✊ 👊 🤛 🤜 👏 🙌 👐 🤲 🙏 💪 🦾 👀 👁️ 👅 👄 👶 🧒 👦 👧 🧑 👨 👩 🧓 👴 👵 👮 🕵️ 💂 👷 🤴 👸 👰 🤵 🎅 🧙 🧚 🧛 🧜 🧝 🦸 🦹")),
        Cat("animal", "حیوانات", "Animals", split("🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🙈 🙉 🙊 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🦇 🐺 🐗 🐴 🦄 🐝 🐛 🦋 🐌 🐞 🐢 🐍 🦎 🐙 🦑 🦀 🐠 🐟 🐬 🐳 🐋 🦈 🐊 🐅 🐆 🦓 🦍 🐘 🦒 🐪 🐎 🐑 🐐 🦌 🐕 🐈 🕊️ 🦚 🦜")),
        Cat("nature", "طبیعت", "Nature", split("🌸 🌼 🌻 🌺 🌹 🌷 🌱 🌿 ☘️ 🍀 🍁 🍂 🍃 🌾 🌵 🌴 🌳 🌲 ⛰️ 🏔️ 🌋 🌊 🔥 💧 ☀️ 🌤️ ⛅ ☁️ 🌧️ ⛈️ 🌩️ ❄️ ☃️ ⛄ 🌈 ⭐ 🌟 ✨ ⚡ 🌙 🌛 🌝 🌞 🪐 🌍 🌎 🌏 ☄️")),
        Cat("food", "غذا", "Food", split("🍎 🍊 🍋 🍌 🍉 🍇 🍓 🍒 🍑 🥭 🍍 🥝 🍅 🥑 🍆 🥕 🌽 🌶️ 🥔 🍞 🥐 🧀 🍳 🥞 🥓 🍔 🍟 🍕 🌭 🌮 🥙 🍝 🍜 🍣 🍤 🍙 🍦 🍩 🍪 🎂 🍰 🍫 🍬 🍭 ☕ 🍵 🥤 🍺 🍷 🥂")),
        Cat("travel", "سفر", "Travel", split("🚗 🚕 🚙 🚌 🏎️ 🚓 🚑 🚒 🚚 🏍️ 🚲 ✈️ 🚀 🛸 🚁 ⛵ 🚢 🚂 🏠 🏰 🕌 🗼 🗽 ⛺ 🏖️ 🏝️ 🗺️ 🧭 🎡 🎢 🎪 🌅 🌄 🌃 🌉 🌌")),
        Cat("fun", "سرگرمی", "Fun", split("🎉 🎊 🎈 🎁 🎂 🎀 🏆 🥇 🥈 🥉 ⚽ 🏀 🏈 ⚾ 🎾 🏐 🎱 🎯 🎮 🕹️ 🎲 🎭 🎨 🎬 🎤 🎧 🎸 🎹 🥁 🎷 🎺 🎻 📷 📹 📱 💻 💡 🔔 🎵 🎶")),
        Cat("symbol", "نمادها", "Symbols", split("✅ ❌ ⭕ ❗ ❓ ‼️ ⚠️ 🚫 💯 🔞 🆒 🆕 🆓 🔝 🔙 🔜 ➕ ➖ ➗ ✖️ ♾️ 💲 ™️ ©️ ®️ ✔️ ☑️ 🔴 🟠 🟡 🟢 🔵 🟣 ⚫ ⚪ 🔶 🔷 🔺 🔻 💠 🔘 ▶️ ⏸️ ⏩ ⏪ 🔁 🔀 📍 📌 🔥 💥 💫 💦 💨 🕳️ 💬 💭 🗯️ 💤")),
        Cat("flag", "پرچم", "Flags", split("🇦🇫 🇮🇷 🇹🇯 🇺🇿 🇹🇷 🇵🇰 🇮🇳 🇸🇦 🇦🇪 🇶🇦 🇮🇶 🇪🇬 🇩🇪 🇫🇷 🇬🇧 🇺🇸 🇨🇦 🇮🇹 🇪🇸 🇳🇱 🇸🇪 🇳🇴 🇷🇺 🇺🇦 🇨🇳 🇯🇵 🇰🇷 🇧🇷 🇦🇷 🇲🇽 🇦🇺 🏳️ 🏴 🏁 🚩"))
    )

    val TEXT_COLORS = ColorRow.PALETTE

    class TextStyle(val fa: String, val en: String, val color: Int, val outline: Float, val outlineColor: Int, val shadow: Float, val bg: Float, val bgColor: Int, val bold: Boolean, val font: String)

    val TEXT_STYLES = listOf(
        TextStyle("ساده", "Plain", 0xFFFFFFFF.toInt(), 0f, 0xFF000000.toInt(), 0f, 0f, 0xFF000000.toInt(), false, "sans"),
        TextStyle("پررنگ", "Bold", 0xFFFFFFFF.toInt(), 0f, 0xFF000000.toInt(), 0.4f, 0f, 0xFF000000.toInt(), true, "sans"),
        TextStyle("کانتور", "Outline", 0xFFFFFFFF.toInt(), 0.6f, 0xFF000000.toInt(), 0f, 0f, 0xFF000000.toInt(), true, "sans"),
        TextStyle("زرد", "Yellow", 0xFFFFD60A.toInt(), 0.5f, 0xFF000000.toInt(), 0.3f, 0f, 0xFF000000.toInt(), true, "black"),
        TextStyle("برچسب", "Label", 0xFF000000.toInt(), 0f, 0xFF000000.toInt(), 0f, 0.95f, 0xFFFFFFFF.toInt(), true, "medium"),
        TextStyle("نوار تیره", "Dark bar", 0xFFFFFFFF.toInt(), 0f, 0xFF000000.toInt(), 0f, 0.7f, 0xFF000000.toInt(), false, "sans"),
        TextStyle("نئون", "Neon", 0xFF00FFEA.toInt(), 0.3f, 0xFF0066FF.toInt(), 0.8f, 0f, 0xFF000000.toInt(), true, "sans"),
        TextStyle("صورتی", "Pink", 0xFFFF4D9D.toInt(), 0.4f, 0xFFFFFFFF.toInt(), 0.2f, 0f, 0xFF000000.toInt(), true, "cursive"),
        TextStyle("کلاسیک", "Classic", 0xFFF5E6C8.toInt(), 0f, 0xFF000000.toInt(), 0.5f, 0f, 0xFF000000.toInt(), false, "serif"),
        TextStyle("تایپ‌رایتر", "Typewriter", 0xFFFFFFFF.toInt(), 0f, 0xFF000000.toInt(), 0.2f, 0.6f, 0xFF222222.toInt(), false, "mono"),
        TextStyle("قرمز", "Red", 0xFFFF3B30.toInt(), 0.5f, 0xFFFFFFFF.toInt(), 0f, 0f, 0xFF000000.toInt(), true, "black"),
        TextStyle("دست‌نویس", "Handwritten", 0xFFFFFFFF.toInt(), 0.2f, 0xFF333333.toInt(), 0.3f, 0f, 0xFF000000.toInt(), false, "casual")
    )
}
