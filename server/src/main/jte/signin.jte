@import me.hejl.kwatern.web.Ui
@param Ui ui
@param String name
@param String next
@param boolean keep
@param String error
@template.layout(ui = ui, title = ui.t("signin.title"), bare = ui.viewer().privateSite(), account = false, content = @`
<main class="signin">
    <form class="card signin-card" action="/sign-in" method="post">
        <div class="signin-head">
            <h1>${ui.t("signin.title")}</h1>
            @if(ui.viewer().privateSite())<p class="muted">${ui.t("signin.private")}</p>@endif
        </div>
        @if(!error.isEmpty())<p class="form-error" role="alert">${error}</p>@endif
        <input type="hidden" name="next" value="${next}">
        <div class="field">
            <label for="name">${ui.t("signin.name")}</label>
            <input id="name" name="name" autocomplete="username" autocapitalize="none" spellcheck="false" required value="${name}" autofocus="${name.isEmpty()}">
        </div>
        <div class="field">
            <label for="password">${ui.t("signin.password")}</label>
            <input id="password" name="password" type="password" autocomplete="current-password" required autofocus="${!name.isEmpty()}">
        </div>
        <label class="check"><input type="checkbox" name="keep" value="1" checked="${keep}">${ui.t("signin.keep")}</label>
        <button type="submit">${ui.t("signin.submit")}</button>
        <p class="card-note">${ui.t("signin.no_account")}</p>
    </form>
</main>
`)
