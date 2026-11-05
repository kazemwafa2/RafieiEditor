#!/bin/bash
# Downloads the bundled assets (20 fonts + the offline Persian speech model) into the app.
# Same sources as the CI workflow (.github/workflows/build.yml). Run from anywhere.
set -e
P="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/assets"
mkdir -p "$P/fonts" "$P/vosk"
B=https://raw.githubusercontent.com/google/fonts/main/ofl
get() { [ -s "$P/fonts/$2" ] && { echo "have $2"; return; }; curl -fsSL --retry 3 --max-time 120 -o "$P/fonts/$2" "$B/$1" && echo "ok $2" || { echo "MISSING $2"; rm -f "$P/fonts/$2"; }; }
get "vazirmatn/Vazirmatn%5Bwght%5D.ttf" Vazirmatn.ttf
get "notonastaliqurdu/NotoNastaliqUrdu%5Bwght%5D.ttf" NotoNastaliqUrdu.ttf
get "amiri/Amiri-Regular.ttf" Amiri.ttf
get "amiri/Amiri-Bold.ttf" AmiriBold.ttf
get "arefruqaa/ArefRuqaa-Regular.ttf" ArefRuqaa.ttf
get "arefruqaa/ArefRuqaa-Bold.ttf" ArefRuqaaBold.ttf
get "katibeh/Katibeh-Regular.ttf" Katibeh.ttf
get "lalezar/Lalezar-Regular.ttf" Lalezar.ttf
get "reemkufi/ReemKufi%5Bwght%5D.ttf" ReemKufi.ttf
get "scheherazadenew/ScheherazadeNew-Regular.ttf" ScheherazadeNew.ttf
get "harmattan/Harmattan-Regular.ttf" Harmattan.ttf
get "markazitext/MarkaziText%5Bwght%5D.ttf" MarkaziText.ttf
get "mirza/Mirza-Regular.ttf" Mirza.ttf
get "rakkas/Rakkas-Regular.ttf" Rakkas.ttf
get "tajawal/Tajawal-Regular.ttf" Tajawal.ttf
get "tajawal/Tajawal-Bold.ttf" TajawalBold.ttf
get "lateef/Lateef-Regular.ttf" Lateef.ttf
get "elmessiri/ElMessiri%5Bwght%5D.ttf" ElMessiri.ttf
get "notonaskharabic/NotoNaskhArabic%5Bwght%5D.ttf" NotoNaskhArabic.ttf
get "jomhuria/Jomhuria-Regular.ttf" Jomhuria.ttf
if [ ! -s "$P/vosk/fa.zip" ]; then
  curl -fsSL --retry 3 --max-time 900 -o "$P/vosk/fa.zip" https://alphacephei.com/vosk/models/vosk-model-small-fa-0.42.zip && echo "ok vosk" || { echo "MISSING vosk"; rm -f "$P/vosk/fa.zip"; }
else echo "have vosk"; fi
echo "fonts: $(ls "$P/fonts" | wc -l)  vosk: $(ls -la "$P/vosk/fa.zip" 2>/dev/null | awk '{print $5}')"
