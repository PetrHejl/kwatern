@import me.hejl.kwatern.web.Ui
@param Ui ui
@param String message
@template.layout(ui = ui, title = message, content = @`
<div class="hero">
    <div class="hero-inner">
        <div class="hero-text">
            <h1>${message}</h1>
            <p class="summary"><a href="/">${ui.t("home.back")}</a></p>
        </div>
    </div>
</div>
`)
