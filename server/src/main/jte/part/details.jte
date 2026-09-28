@import java.util.List
@import me.hejl.kwatern.view.Pages.NoteView
@import me.hejl.kwatern.view.Pages.Row
@import me.hejl.kwatern.view.Pages.SourceUse
@import me.hejl.kwatern.web.Ui
@param Ui ui
@param List<Row> attributes
@param List<NoteView> notes
@param List<SourceUse> sources
@if(!attributes.isEmpty())
<section class="card">
    <h2>${ui.t("attributes")}</h2>
    <dl class="rows">
    @for(var a : attributes)
        <dt>${a.label()}</dt><dd>${a.value()}</dd>
    @endfor
    </dl>
</section>
@endif
@if(!notes.isEmpty())
<section class="card">
    <h2>${ui.t("notes")}</h2>
    @for(var n : notes)
        <div class="${n.preformatted() ? "note preformatted" : "note"}">${n.text()}</div>
    @endfor
</section>
@endif
@if(!sources.isEmpty())
<section class="card">
    <h2>${ui.t("sources.all")}</h2>
    <ul class="source-list">
    @for(var s : sources)
        <li>@if(!s.url().isEmpty())<a href="${s.url()}">${s.source()}</a>@else${s.source()}@endif@if(!s.page().isEmpty()), ${s.page()}@endif <span class="muted">· ${s.supports()}</span></li>
    @endfor
    </ul>
</section>
@endif
