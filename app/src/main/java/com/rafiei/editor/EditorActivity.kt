package com.rafiei.editor

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class ToolItem(val icon: String, val label: String, val tint: Int = Color.WHITE, val action: () -> Unit)

class EditorActivity : Activity(), TimelineListener, OverlayListener, EngineListener {
    companion object {
        const val REQ_CLIPS = 101
        const val REQ_AUDIO = 102
        const val REQ_PIP = 103
        const val REQ_STICKER_IMG = 104
        const val REQ_FONT = 105
        const val REQ_MIC = 201
    }

    lateinit var proj: Project
    lateinit var stage: PreviewStage
    lateinit var engine: PreviewEngine
    lateinit var timeline: TimelineView
    lateinit var panelHost: FrameLayout
    lateinit var timelineRow: LinearLayout
    private lateinit var topBar: FrameLayout
    private lateinit var controlBar: FrameLayout
    private lateinit var bottomArea: LinearLayout
    private lateinit var playIcon: IconView
    private lateinit var undoIcon: IconView
    private lateinit var redoIcon: IconView
    private lateinit var okIcon: IconView
    private lateinit var fullIcon: IconView
    private lateinit var muteBtn: ToolBtn

    val undo = ArrayList<String>()
    val redo = ArrayList<String>()
    var panelOpen = false
    private var panelSnap = ""
    private var panelOnApply: (() -> Unit)? = null
    private var panelOnCancel: (() -> Unit)? = null
    private var fullscreen = false
    private var saveScheduled = false
    private var pendingMicAction: (() -> Unit)? = null
    private var importAfterAudio: ((AudioItem) -> Unit)? = null
    private var pickCallback: ((List<Uri>) -> Unit)? = null
    private var pickReq = 0

    val isPhoto: Boolean get() = proj.type == "photo"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val pid = intent.getStringExtra("pid") ?: ""
        val loaded = Store.load(this, pid)
        if (loaded == null) {
            finish()
            return
        }
        proj = loaded
        buildUi()
        timeline.project = proj
        timeline.photoMode = isPhoto
        timeline.relayout()
        if (proj.clips.isEmpty()) {
            if (intent.getBooleanExtra("pick", false)) {
                window.decorView.post { pickClips() }
            }
        } else {
            window.decorView.post {
                engine.seek(0)
                refreshToolbar()
            }
        }
        updateUndoButtons()
    }

    // ---------------------------------------------------------------- UI construction

    private fun buildUi() {
        val root = lin(true)
        root.setBackgroundColor(C.BG)

        // top bar
        topBar = FrameLayout(this)
        val back = IconView(this, "back")
        back.setOnClickListener { onBackPressed() }
        topBar.addView(back, fl(dp(48), dp(48), Gravity.START or Gravity.CENTER_VERTICAL))
        val help = IconView(this, "help")
        help.setOnClickListener {
            Dialogs.message(
                this, L("راهنما", "Help"), L(
                    "• برای پیمایش، تایم‌لاین را بکشید.\n• روی کلیپ بزنید تا ابزارهایش باز شود؛ دسته‌های سفید برای برش است.\n• فشار طولانی روی کلیپ، جابه‌جایی و ترتیب را عوض می‌کند.\n• متن و استیکر را با انگشت جابه‌جا و بزرگ کنید.\n• دکمه‌ی «خروجی» ویدیو را با کیفیت و مدت دقیق ذخیره می‌کند.",
                    "• Drag the timeline to scrub.\n• Tap a clip to see its tools; white handles trim it.\n• Long-press to move or reorder.\n• Drag text and stickers with your fingers.\n• Export saves the full-length video."
                )
            )
        }
        topBar.addView(help, fl(dp(44), dp(44), Gravity.START or Gravity.CENTER_VERTICAL).also { it.leftMargin = dp(48) })
        val export = tv(L("خروجی", "Export"), 14f, Color.WHITE, true)
        export.background = gradBg(C.ACCENT2, C.ACCENT, dpf(18f), android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT)
        export.setPadding(dp(18), dp(7), dp(18), dp(7))
        export.setOnClickListener { startExport() }
        topBar.addView(export, fl(WRAP, WRAP, Gravity.END or Gravity.CENTER_VERTICAL).also { it.rightMargin = dp(12) })
        root.addView(topBar, lp(MATCH, dp(48)))

        // preview
        val previewHost = FrameLayout(this)
        previewHost.setBackgroundColor(Color.BLACK)
        stage = PreviewStage(this)
        previewHost.addView(stage, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        root.addView(previewHost, lp(MATCH, 0, 1f))
        stage.top.listener = this

        // control bar
        controlBar = FrameLayout(this)
        controlBar.setBackgroundColor(C.BG)
        val ur = lin(false)
        undoIcon = IconView(this, "undo"); redoIcon = IconView(this, "redo")
        undoIcon.setOnClickListener { doUndo() }
        redoIcon.setOnClickListener { doRedo() }
        ur.addView(undoIcon, lp(dp(44), dp(44)))
        ur.addView(redoIcon, lp(dp(44), dp(44)))
        controlBar.addView(ur, fl(WRAP, dp(48), Gravity.START or Gravity.CENTER_VERTICAL).also { it.leftMargin = dp(6) })
        playIcon = IconView(this, "play")
        playIcon.padFrac = 0.12f
        playIcon.setOnClickListener { if (!isPhoto) engine.toggle() }
        controlBar.addView(playIcon, fl(dp(48), dp(48), Gravity.CENTER))
        fullIcon = IconView(this, "fullscreen")
        fullIcon.setOnClickListener { toggleFullscreen() }
        controlBar.addView(fullIcon, fl(dp(44), dp(44), Gravity.END or Gravity.CENTER_VERTICAL).also { it.rightMargin = dp(8) })
        okIcon = IconView(this, "check", C.GREEN)
        okIcon.setOnClickListener { closePanel(true) }
        okIcon.visibility = View.GONE
        controlBar.addView(okIcon, fl(dp(48), dp(44), Gravity.END or Gravity.CENTER_VERTICAL).also { it.rightMargin = dp(8) })
        root.addView(controlBar, lp(MATCH, dp(48)))
        if (isPhoto) playIcon.visibility = View.GONE

        // bottom area
        bottomArea = lin(true)
        panelHost = FrameLayout(this)
        panelHost.setBackgroundColor(C.BG)
        bottomArea.addView(panelHost, lp(MATCH, WRAP))
        timelineRow = lin(false)
        timelineRow.setBackgroundColor(C.BG)
        val left = lin(true)
        left.gravity = Gravity.CENTER_HORIZONTAL
        val plus = IconView(this, "plus")
        plus.padFrac = 0.22f
        plus.background = gradBg(0xFFFF6B8B.toInt(), 0xFFFF3D5A.toInt(), dpf(10f))
        plus.setOnClickListener { onAddClip() }
        left.addView(plus, lp(dp(40), dp(40)).also { it.topMargin = dp(26) })
        muteBtn = ToolBtn(this, "volume", L("بی‌صدا", "Mute"), 56) { toggleMuteAll() }
        left.addView(muteBtn, lp(dp(60), dp(50)).also { it.topMargin = dp(6) })
        timelineRow.addView(left, lp(dp(66), MATCH))
        timeline = TimelineView(this)
        timeline.listener = this
        timelineRow.addView(timeline, lp(0, MATCH, 1f))
        bottomArea.addView(timelineRow, lp(MATCH, dp(172)))
        root.addView(bottomArea, lp(MATCH, WRAP))
        if (isPhoto) timelineRow.visibility = View.GONE
        setContentView(root)

        engine = PreviewEngine(this, stage, { proj }, this)
        var lw = 0
        var lh = 0
        stage.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (v.width > 0 && (v.width != lw || v.height != lh)) {
                lw = v.width
                lh = v.height
                v.post { engine.refresh() }
            }
        }
    }

    // ---------------------------------------------------------------- listeners from engine / timeline

    override fun onTime(ms: Long) {
        timeline.setTimeSilently(ms)
    }

    override fun onPlayState(playing: Boolean) {
        playIcon.set(if (playing) "pause" else "play", Color.WHITE)
    }

    override fun onSeek(ms: Long) {
        if (engine.playing) engine.pause()
        engine.seek(ms)
    }

    override fun onSelect(sel: Sel?) {
        stage.top.selectedId = if (sel != null && sel.kind == "overlay") sel.id else null
        stage.top.invalidate()
        refreshToolbar()
    }

    override fun onEditBegin() {
        beginChange()
    }

    override fun onEditing() {
        engine.refresh()
        scheduleSave()
    }

    override fun onEditEnd() {
        changed()
    }

    override fun onAddClip() {
        pickClips()
    }

    override fun onTransition(index: Int) {
        openTransitionPanel(index)
    }

    // overlay listener (from preview)
    override fun onOverlaySelected(id: String?) {
        timeline.selection = if (id == null) null else Sel("overlay", id)
        timeline.invalidate()
        refreshToolbar()
    }

    override fun onOverlayLive() {
        scheduleSave()
    }

    override fun onOverlayCommit() {
        changed()
    }

    override fun onOverlayBegin() {
        beginChange()
    }

    override fun onOverlayDelete(id: String) {
        beginChange()
        proj.overlays.removeAll { it.id == id }
        timeline.selection = null
        stage.top.selectedId = null
        changed()
        refreshToolbar()
    }

    override fun onOverlayCopy(id: String) {
        val o = proj.overlays.firstOrNull { it.id == id } ?: return
        beginChange()
        val c = o.copyOf()
        c.cx = clampF(o.cx + 0.05f, 0f, 1f)
        c.cy = clampF(o.cy + 0.05f, 0f, 1f)
        proj.overlays.add(c)
        timeline.selection = Sel("overlay", c.id)
        stage.top.selectedId = c.id
        changed()
        refreshToolbar()
    }

    override fun onOverlayEdit(id: String) {
        val o = proj.overlays.firstOrNull { it.id == id } ?: return
        if (o.kind == "text") openTextPanel(o, null)
    }

    // ---------------------------------------------------------------- state helpers

    fun beginChange() {
        undo.add(proj.snapshot())
        if (undo.size > 60) undo.removeAt(0)
        redo.clear()
        updateUndoButtons()
    }

    fun changed() {
        timeline.relayout()
        engine.refresh()
        updateUndoButtons()
        updateMuteButton()
        scheduleSave()
    }

    private fun updateUndoButtons() {
        if (!::undoIcon.isInitialized) return
        undoIcon.set("undo", if (undo.isEmpty()) 0xFF666666.toInt() else Color.WHITE)
        redoIcon.set("redo", if (redo.isEmpty()) 0xFF666666.toInt() else Color.WHITE)
    }

    private fun updateMuteButton() {
        muteBtn.iconView.set(if (proj.muteClips) "mute" else "volume", if (proj.muteClips) C.ACCENT else Color.WHITE)
        muteBtn.labelView.setTextColor(if (proj.muteClips) C.ACCENT else C.TXT2)
    }

    private fun toggleMuteAll() {
        beginChange()
        proj.muteClips = !proj.muteClips
        changed()
    }

    private fun doUndo() {
        if (panelOpen || undo.isEmpty()) return
        redo.add(proj.snapshot())
        proj.restore(undo.removeAt(undo.size - 1))
        afterRestore()
    }

    private fun doRedo() {
        if (panelOpen || redo.isEmpty()) return
        undo.add(proj.snapshot())
        proj.restore(redo.removeAt(redo.size - 1))
        afterRestore()
    }

    private fun afterRestore() {
        val s = timeline.selection
        if (s != null && !exists(s)) timeline.selection = null
        stage.top.selectedId = if (timeline.selection?.kind == "overlay") timeline.selection?.id else null
        changed()
        refreshToolbar()
    }

    private fun exists(s: Sel): Boolean {
        return when (s.kind) {
            "clip" -> proj.clips.any { it.id == s.id }
            "overlay" -> proj.overlays.any { it.id == s.id }
            "audio" -> proj.audios.any { it.id == s.id }
            "fx" -> proj.fxs.any { it.id == s.id }
            else -> false
        }
    }

    fun selClip(): Clip? {
        val s = timeline.selection ?: return null
        if (s.kind != "clip") return null
        return proj.clips.firstOrNull { it.id == s.id }
    }

    fun selOverlay(): Overlay? {
        val s = timeline.selection ?: return null
        if (s.kind != "overlay") return null
        return proj.overlays.firstOrNull { it.id == s.id }
    }

    fun selAudio(): AudioItem? {
        val s = timeline.selection ?: return null
        if (s.kind != "audio") return null
        return proj.audios.firstOrNull { it.id == s.id }
    }

    fun selFx(): FxItem? {
        val s = timeline.selection ?: return null
        if (s.kind != "fx") return null
        return proj.fxs.firstOrNull { it.id == s.id }
    }

    /** The selected clip, or the clip under the playhead. */
    fun targetClip(): Clip? {
        val c = selClip()
        if (c != null) return c
        val i = proj.clipIndexAt(engine.timeMs)
        return if (i >= 0) proj.clips[i] else null
    }

    fun toast(s: String) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    }

    // ---------------------------------------------------------------- saving

    fun scheduleSave() {
        if (saveScheduled) return
        saveScheduled = true
        Bg.postDelayed(1200) {
            saveScheduled = false
            saveNow()
        }
    }

    fun saveNow() {
        if (!::proj.isInitialized) return
        proj.modified = System.currentTimeMillis()
        val json = proj.snapshot()
        val ctx = applicationContext
        val id = proj.id
        val hasClips = proj.clips.isNotEmpty()
        Bg.run {
            Store.saveJson(ctx, id, json)
            if (hasClips) {
                val q = Project.fromJson(JSONObject(json))
                MediaUtil.saveThumb(ctx, q)
            }
        }
    }

    private fun startExport() {
        if (proj.clips.isEmpty()) {
            toast(L("ابتدا ویدیو یا عکس اضافه کنید.", "Add a video or photo first."))
            return
        }
        engine.pause()
        proj.modified = System.currentTimeMillis()
        Store.saveJson(this, proj.id, proj.snapshot())
        val i = Intent(this, ExportActivity::class.java)
        i.putExtra("pid", proj.id)
        startActivity(i)
    }

    // ---------------------------------------------------------------- panels and toolbars

    /** Replace the bottom toolbar with [view]. Changes made while it is open are one undo step; back cancels. */
    fun openPanel(view: View, hideTimeline: Boolean = false, preSnap: String? = null, onApply: (() -> Unit)? = null, onCancel: (() -> Unit)? = null) {
        if (panelOpen) closePanel(true)
        panelSnap = preSnap ?: proj.snapshot()
        panelOpen = true
        panelOnApply = onApply
        panelOnCancel = onCancel
        panelHost.removeAllViews()
        panelHost.addView(view, FrameLayout.LayoutParams(MATCH, WRAP))
        timelineRow.visibility = if (hideTimeline || isPhoto) View.GONE else View.VISIBLE
        okIcon.visibility = View.VISIBLE
        fullIcon.visibility = View.GONE
    }

    fun closePanel(apply: Boolean) {
        if (!panelOpen) return
        panelOpen = false
        val cb = if (apply) panelOnApply else panelOnCancel
        panelOnApply = null
        panelOnCancel = null
        if (apply) {
            if (proj.snapshot() != panelSnap) {
                undo.add(panelSnap)
                if (undo.size > 60) undo.removeAt(0)
                redo.clear()
            }
        } else {
            proj.restore(panelSnap)
            val s = timeline.selection
            if (s != null && !exists(s)) timeline.selection = null
            stage.top.selectedId = if (timeline.selection?.kind == "overlay") timeline.selection?.id else null
        }
        okIcon.visibility = View.GONE
        fullIcon.visibility = View.VISIBLE
        timelineRow.visibility = if (isPhoto) View.GONE else View.VISIBLE
        try {
            cb?.invoke()
        } catch (e: Exception) {
        }
        changed()
        refreshToolbar()
    }

    fun setToolbar(items: List<ToolItem>) {
        panelHost.removeAllViews()
        val sv = HorizontalScrollView(this)
        sv.isHorizontalScrollBarEnabled = false
        val row = lin(false)
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(6), 0, dp(6), 0)
        for (it in items) {
            val b = ToolBtn(this, it.icon, it.label, 68, it.tint) { it.action() }
            row.addView(b, lp(dp(68), dp(62)))
        }
        sv.addView(row)
        panelHost.addView(sv, FrameLayout.LayoutParams(MATCH, dp(66)))
    }

    fun refreshToolbar() {
        if (panelOpen || !::panelHost.isInitialized) return
        val s = timeline.selection
        if (s == null) {
            mainToolbar()
            return
        }
        when (s.kind) {
            "clip" -> clipToolbar()
            "overlay" -> overlayToolbar()
            "audio" -> audioToolbar()
            "fx" -> fxToolbar()
            else -> mainToolbar()
        }
    }

    private fun mainToolbar() {
        val items = ArrayList<ToolItem>()
        items.add(ToolItem("canvas", L("بوم", "Canvas")) { openCanvasPanel() })
        if (!isPhoto) items.add(ToolItem("audio", L("صدا", "Audio")) { openAudioMenu() })
        items.add(ToolItem("sticker", L("استیکر", "Sticker")) { openStickerPanel() })
        items.add(ToolItem("text", L("متن", "Text")) { openTextPanel(null, null) })
        if (!isPhoto) items.add(ToolItem("effect", L("افکت", "Effect")) { openEffectPanel() })
        items.add(ToolItem("filter", L("فیلتر", "Filter")) { openFilterPanel(0) })
        items.add(ToolItem("adjust", L("تنظیم", "Adjust")) { openFilterPanel(1) })
        items.add(ToolItem("crop", L("برش/زوم", "Crop")) { openCropPanel() })
        items.add(ToolItem("pip", L("عکس روی ویدیو", "PIP")) { pickPip() })
        if (!isPhoto) items.add(ToolItem("speed", L("سرعت", "Speed")) {
            val c = targetClip()
            if (c == null || c.isImage) toast(L("یک ویدیو انتخاب کنید.", "Select a video clip.")) else {
                timeline.selection = Sel("clip", c.id)
                openSpeedPanel()
            }
        })
        setToolbar(items)
    }

    private fun clipToolbar() {
        val c = selClip() ?: return mainToolbar()
        val items = ArrayList<ToolItem>()
        if (!isPhoto) items.add(ToolItem("split", L("برش", "Split")) { splitAtPlayhead() })
        if (!c.isImage) items.add(ToolItem("speed", L("سرعت", "Speed")) { openSpeedPanel() })
        if (c.isImage && !isPhoto) items.add(ToolItem("trim", L("مدت", "Duration")) { openDurationPanel() })
        if (!c.isImage) items.add(ToolItem("volume", L("صدا", "Volume")) { openVolumePanel() })
        if (!isPhoto) items.add(ToolItem("anim", L("انیمیشن", "Animate")) { openAnimPanel() })
        items.add(ToolItem("rotate", L("چرخش", "Rotate")) { mutateClip { it.rot = (it.rot + 90) % 360; it.zoom = 1f; it.panX = 0f; it.panY = 0f } })
        items.add(ToolItem("flip", L("آینه", "Mirror")) { mutateClip { it.flipH = !it.flipH } })
        items.add(ToolItem("crop", L("برش/زوم", "Crop")) { openCropPanel() })
        items.add(ToolItem("canvas", if (c.fill) L("پر کردن", "Fill") else L("جا دادن", "Fit")) { mutateClip { it.fill = !it.fill; it.panX = 0f; it.panY = 0f }; refreshToolbar() })
        items.add(ToolItem("filter", L("فیلتر", "Filter")) { openFilterPanel(0) })
        items.add(ToolItem("adjust", L("تنظیم", "Adjust")) { openFilterPanel(1) })
        if (!isPhoto) {
            items.add(ToolItem("copy", L("کپی", "Duplicate")) { duplicateClip() })
            if (!c.isImage) items.add(ToolItem("freeze", L("توقف تصویر", "Freeze")) { freezeFrame() })
            if (!c.isImage && c.hasAudio) items.add(ToolItem("music", L("جداسازی صدا", "Extract")) { extractAudio() })
            items.add(ToolItem("delete", L("حذف", "Delete"), C.ACCENT) { deleteClip() })
        }
        setToolbar(items)
    }

    private fun overlayToolbar() {
        val o = selOverlay() ?: return mainToolbar()
        val items = ArrayList<ToolItem>()
        if (o.kind == "text") items.add(ToolItem("keyboard", L("ویرایش", "Edit")) { openTextPanel(o, null) })
        items.add(ToolItem("anim", L("انیمیشن", "Animate")) { openOverlayAnimPanel() })
        items.add(ToolItem("opacity", L("شفافیت", "Opacity")) { openOpacityPanel() })
        items.add(ToolItem("flip", L("آینه", "Mirror")) { beginChange(); o.flipH = !o.flipH; changed() })
        items.add(ToolItem("split", L("برش", "Split")) { splitOverlay(o) })
        items.add(ToolItem("copy", L("کپی", "Duplicate")) { onOverlayCopy(o.id) })
        items.add(ToolItem("delete", L("حذف", "Delete"), C.ACCENT) { onOverlayDelete(o.id) })
        setToolbar(items)
    }

    private fun audioToolbar() {
        val a = selAudio() ?: return mainToolbar()
        val items = ArrayList<ToolItem>()
        items.add(ToolItem("volume", L("صدا", "Volume")) { openAudioVolumePanel() })
        items.add(ToolItem("speed", L("سرعت", "Speed")) { openAudioSpeedPanel() })
        items.add(ToolItem("split", L("برش", "Split")) { splitAudio(a) })
        items.add(ToolItem("copy", L("کپی", "Duplicate")) {
            beginChange()
            val c = a.copyOf()
            c.startMs = a.endMs()
            proj.audios.add(c)
            timeline.selection = Sel("audio", c.id)
            changed()
            refreshToolbar()
        })
        items.add(ToolItem("delete", L("حذف", "Delete"), C.ACCENT) {
            beginChange()
            proj.audios.removeAll { it.id == a.id }
            timeline.selection = null
            changed()
            refreshToolbar()
        })
        setToolbar(items)
    }

    private fun fxToolbar() {
        val f = selFx() ?: return mainToolbar()
        val items = ArrayList<ToolItem>()
        items.add(ToolItem("adjust", L("شدت", "Intensity")) { openFxIntensityPanel() })
        items.add(ToolItem("effect", L("تعویض", "Replace")) { openEffectPanel() })
        items.add(ToolItem("copy", L("کپی", "Duplicate")) {
            beginChange()
            val c = f.copyOf()
            c.startMs = f.endMs()
            proj.fxs.add(c)
            timeline.selection = Sel("fx", c.id)
            changed()
            refreshToolbar()
        })
        items.add(ToolItem("delete", L("حذف", "Delete"), C.ACCENT) {
            beginChange()
            proj.fxs.removeAll { it.id == f.id }
            timeline.selection = null
            changed()
            refreshToolbar()
        })
        setToolbar(items)
    }

    // ---------------------------------------------------------------- clip operations

    fun mutateClip(block: (Clip) -> Unit) {
        val c = targetClip() ?: return
        beginChange()
        block(c)
        changed()
    }

    private fun splitAtPlayhead() {
        val t = engine.timeMs
        val i = proj.clipIndexAt(t)
        if (i < 0) return
        val c = proj.clips[i]
        val lt = t - proj.clipStart(i)
        val d = c.durMs()
        if (lt < 150 || lt > d - 150) {
            toast(L("نشانگر را داخل کلیپ ببرید.", "Move the playhead inside the clip."))
            return
        }
        beginChange()
        val b = c.copyOf()
        if (c.isImage) {
            c.imgDur = lt
            b.imgDur = d - lt
        } else {
            val cut = c.trimStart + (lt * c.speed).toLong()
            c.trimEnd = cut
            b.trimStart = cut
        }
        c.animOut = "none"
        b.animIn = "none"
        b.trans = "none"
        proj.clips.add(i + 1, b)
        timeline.selection = Sel("clip", c.id)
        changed()
        refreshToolbar()
    }

    private fun duplicateClip() {
        val c = selClip() ?: return
        val i = proj.clips.indexOf(c)
        beginChange()
        val d = c.copyOf()
        d.trans = "none"
        proj.clips.add(i + 1, d)
        timeline.selection = Sel("clip", d.id)
        changed()
        refreshToolbar()
    }

    private fun deleteClip() {
        val c = selClip() ?: return
        if (proj.clips.size == 1 && proj.type == "video") {
            Dialogs.confirm(this, L("حذف", "Delete"), L("آخرین کلیپ حذف شود؟", "Delete the last clip?"), L("حذف", "Delete")) { doDelete(c) }
        } else doDelete(c)
    }

    private fun doDelete(c: Clip) {
        beginChange()
        proj.clips.remove(c)
        timeline.selection = null
        changed()
        refreshToolbar()
    }

    private fun freezeFrame() {
        val t = engine.timeMs
        val i = proj.clipIndexAt(t)
        if (i < 0) return
        val c = proj.clips[i]
        if (c.isImage) return
        val lt = t - proj.clipStart(i)
        val srcT = c.trimStart + (lt * c.speed).toLong()
        val dlg = progressDialog(L("در حال ساخت تصویر ثابت…", "Creating freeze frame…"))
        val pid = proj.id
        val ctx = applicationContext
        Bg.run {
            val b = MediaUtil.frameAt(MediaUtil.pathOf(c.uri), srcT, 2560, true)
            Bg.ui {
                dlg.dismiss()
                if (b == null) {
                    toast(L("خطا در گرفتن فریم", "Could not grab the frame"))
                } else {
                    val f = File(Store.mediaDir(ctx, pid), uid() + ".jpg")
                    FileOutputStream(f).use { b.compress(android.graphics.Bitmap.CompressFormat.JPEG, 94, it) }
                    beginChange()
                    val img = Clip()
                    img.uri = Uri.fromFile(f).toString()
                    img.isImage = true
                    img.w = c.w; img.h = c.h; img.hasAudio = false
                    img.imgDur = 2000
                    img.rot = c.rot; img.flipH = c.flipH; img.flipV = c.flipV
                    img.filter = c.filter; img.filterAmt = c.filterAmt; img.adj = c.adj.copyOf()
                    img.fill = c.fill
                    if (lt < 150) {
                        proj.clips.add(i, img)
                    } else if (lt > c.durMs() - 150) {
                        proj.clips.add(i + 1, img)
                    } else {
                        val second = c.copyOf()
                        c.trimEnd = srcT
                        second.trimStart = srcT
                        c.animOut = "none"; second.animIn = "none"; second.trans = "none"
                        proj.clips.add(i + 1, img)
                        proj.clips.add(i + 2, second)
                    }
                    timeline.selection = Sel("clip", img.id)
                    changed()
                    refreshToolbar()
                }
            }
        }
    }

    private fun extractAudio() {
        val c = selClip() ?: return
        val i = proj.clips.indexOf(c)
        beginChange()
        val a = AudioItem()
        a.uri = c.uri
        a.name = L("صدای ویدیو", "Video audio")
        a.kind = "music"
        a.startMs = proj.clipStart(i)
        a.srcDurMs = c.srcDurMs
        a.trimStart = c.trimStart
        a.trimEnd = c.trimEnd
        a.speed = c.speed
        a.volume = c.volume
        proj.audios.add(a)
        c.volume = 0f
        timeline.selection = Sel("audio", a.id)
        changed()
        refreshToolbar()
        toast(L("صدا جدا شد و روی ترک صدا قرار گرفت.", "Audio extracted to its own track."))
    }

    private fun splitOverlay(o: Overlay) {
        val t = engine.timeMs
        if (t <= o.startMs + 150 || t >= o.endMs() - 150) {
            toast(L("نشانگر را داخل آیتم ببرید.", "Move the playhead inside the item."))
            return
        }
        beginChange()
        val b = o.copyOf()
        b.startMs = t
        b.durMs = o.endMs() - t
        o.durMs = t - o.startMs
        proj.overlays.add(b)
        changed()
    }

    private fun splitAudio(a: AudioItem) {
        val t = engine.timeMs
        if (t <= a.startMs + 150 || t >= a.endMs() - 150) {
            toast(L("نشانگر را داخل آیتم ببرید.", "Move the playhead inside the item."))
            return
        }
        beginChange()
        val b = a.copyOf()
        val cut = a.trimStart + ((t - a.startMs) * a.speed).toLong()
        b.startMs = t
        b.trimStart = cut
        a.trimEnd = cut
        a.fadeOut = 0
        b.fadeIn = 0
        proj.audios.add(b)
        changed()
    }

    fun addOverlay(o: Overlay) {
        val total = Math.max(proj.totalMs(), 1000L)
        if (isPhoto) {
            o.startMs = 0
            o.durMs = total
        } else {
            val start = Math.min(engine.timeMs, Math.max(0L, total - 500))
            o.startMs = start
            o.durMs = Math.max(500L, Math.min(3000L, total - start))
        }
        proj.overlays.add(o)
        timeline.selection = Sel("overlay", o.id)
        stage.top.selectedId = o.id
        changed()
    }

    fun addFx(f: FxItem) {
        val total = Math.max(proj.totalMs(), 1000L)
        val start = Math.min(engine.timeMs, Math.max(0L, total - 500))
        f.startMs = start
        f.durMs = Math.max(500L, Math.min(f.durMs, total - start))
        proj.fxs.add(f)
        timeline.selection = Sel("fx", f.id)
        changed()
    }

    // ---------------------------------------------------------------- media picking and importing

    fun pick(mimes: Array<String>, multiple: Boolean, req: Int, cb: (List<Uri>) -> Unit) {
        pickCallback = cb
        pickReq = req
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "*/*"
        i.putExtra(Intent.EXTRA_MIME_TYPES, mimes)
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
        try {
            startActivityForResult(i, req)
        } catch (e: Exception) {
            toast(L("انتخاب‌گر فایل پیدا نشد", "No file picker available"))
        }
    }

    fun pickClips() {
        if (isPhoto) {
            pick(arrayOf("image/*"), false, REQ_CLIPS) { importClips(it) }
        } else {
            pick(arrayOf("video/*", "image/*"), true, REQ_CLIPS) { importClips(it) }
        }
    }

    fun pickPip() {
        pick(arrayOf("image/*"), false, REQ_PIP) { uris -> importOverlayImage(uris, "image") }
    }

    fun pickStickerImage() {
        pick(arrayOf("image/*"), false, REQ_STICKER_IMG) { uris -> importOverlayImage(uris, "image") }
    }

    fun pickAudio(cb: (AudioItem) -> Unit) {
        pick(arrayOf("audio/*", "video/*"), false, REQ_AUDIO) { uris ->
            if (uris.isNotEmpty()) importAudioUri(uris[0], "music", cb)
        }
    }

    fun importAudioUri(uri: Uri, kind: String, cb: (AudioItem) -> Unit) {
        val dlg = progressDialog(L("در حال وارد کردن صدا…", "Importing audio…"))
        val ctx = applicationContext
        val pid = proj.id
        Bg.run {
            val a = MediaUtil.importAudio(ctx, uri, pid, kind)
            Bg.ui {
                dlg.dismiss()
                if (a == null) toast(L("این فایل صوتی پشتیبانی نمی‌شود", "Unsupported audio file")) else cb(a)
            }
        }
    }

    private fun importOverlayImage(uris: List<Uri>, kind: String) {
        if (uris.isEmpty()) return
        val dlg = progressDialog(L("در حال وارد کردن…", "Importing…"))
        val ctx = applicationContext
        val pid = proj.id
        Bg.run {
            val bmp = MediaUtil.loadBitmap(ctx, uris[0], 1400)
            var path: String? = null
            var aspect = 1f
            if (bmp != null) {
                val f = File(Store.mediaDir(ctx, pid), uid() + ".png")
                FileOutputStream(f).use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                path = Uri.fromFile(f).toString()
                aspect = bmp.width.toFloat() / bmp.height
            }
            Bg.ui {
                dlg.dismiss()
                if (path == null) toast(L("تصویر خوانده نشد", "Could not read the image")) else {
                    beginChange()
                    val o = Overlay()
                    o.kind = kind
                    o.uri = path
                    o.imgAspect = aspect
                    addOverlay(o)
                    refreshToolbar()
                }
            }
        }
    }

    private fun importClips(uris: List<Uri>) {
        if (uris.isEmpty()) {
            if (proj.clips.isEmpty()) {
                Store.delete(this, proj.id)
                finish()
            }
            return
        }
        val dlg = progressDialog(L("در حال وارد کردن رسانه…", "Importing media…"))
        val ctx = applicationContext
        val pid = proj.id
        Bg.run {
            val out = ArrayList<Clip>()
            for (u in uris) {
                val c = MediaUtil.importClip(ctx, u, pid)
                if (c != null) out.add(c)
            }
            Bg.ui {
                dlg.dismiss()
                if (out.isEmpty()) {
                    toast(L("فایل پشتیبانی نمی‌شود", "Unsupported file"))
                    if (proj.clips.isEmpty()) {
                        Store.delete(this, proj.id)
                        finish()
                    }
                } else {
                    beginChange()
                    if (isPhoto) {
                        proj.clips.clear()
                        out[0].imgDur = 3000
                        proj.clips.add(out[0])
                    } else {
                        proj.clips.addAll(out)
                    }
                    if (proj.clips.size == out.size) engine.seek(0) else engine.seek(proj.totalMs() - out[out.size - 1].durMs() + 1)
                    timeline.selection = null
                    changed()
                    refreshToolbar()
                    saveNow()
                }
            }
        }
    }

    fun progressDialog(msg: String): AlertDialog {
        val d = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setMessage(msg).setCancelable(false).create()
        d.show()
        return d
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == pickReq && pickCallback != null) {
            val cb = pickCallback
            pickCallback = null
            val list = ArrayList<Uri>()
            if (resultCode == RESULT_OK && data != null) {
                val cd = data.clipData
                if (cd != null) {
                    for (i in 0 until cd.itemCount) list.add(cd.getItemAt(i).uri)
                } else if (data.data != null) {
                    list.add(data.data!!)
                }
                for (u in list) {
                    try {
                        contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (e: Exception) {
                    }
                }
            }
            cb?.invoke(list)
        }
    }

    fun withMicPermission(action: () -> Unit) {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            pendingMicAction = action
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), REQ_MIC)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_MIC) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pendingMicAction?.invoke()
            } else {
                toast(L("اجازه‌ی میکروفون داده نشد", "Microphone permission denied"))
            }
            pendingMicAction = null
        }
    }

    // ---------------------------------------------------------------- misc

    private fun toggleFullscreen() {
        fullscreen = !fullscreen
        topBar.visibility = if (fullscreen) View.GONE else View.VISIBLE
        bottomArea.visibility = if (fullscreen) View.GONE else View.VISIBLE
        fullIcon.set(if (fullscreen) "close" else "fullscreen", Color.WHITE)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (fullscreen) {
            toggleFullscreen()
            return
        }
        if (panelOpen) {
            closePanel(false)
            return
        }
        if (timeline.selection != null) {
            timeline.selection = null
            stage.top.selectedId = null
            timeline.invalidate()
            refreshToolbar()
            return
        }
        saveNow()
        super.onBackPressed()
    }

    override fun onPause() {
        super.onPause()
        if (::engine.isInitialized) engine.pause()
        if (::proj.isInitialized) saveNow()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::engine.isInitialized) engine.release()
    }
}
