@import gg.jte.Content
@import me.hejl.kwatern.web.Site
@import me.hejl.kwatern.web.Ui
@import me.hejl.kwatern.web.Urls
@param Ui ui
@param String title
@param Content content
@param boolean map = false
@param boolean bare = false
@param boolean account = true
<!DOCTYPE html>
<html lang="${ui.language()}" data-theme="${ui.theme()}">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>${title} · ${ui.t("site.title")}</title>
    @if(map)<link rel="stylesheet" href="/static/leaflet/leaflet.css">@endif
    <link rel="stylesheet" href="${Urls.stylesheet()}">
    @if(map)<script src="/static/leaflet/leaflet.js" defer></script><script src="/static/map.js" defer></script>@endif
</head>
<body class="${ui.genderColours() ? "gender-colours" : ""}">
<header class="site">
    @if(bare)
    <span class="brand">${ui.t("site.title")}</span>
    @else
    <a class="brand" href="/">${ui.t("site.title")}</a>
    <nav><a href="/surnames">${ui.t("nav.surnames")}</a><a href="/places">${ui.t("nav.places")}</a><a href="/sources">${ui.t("nav.sources")}</a>@if(ui.hasMedia())<a href="/photos">${ui.t("nav.photos")}</a>@endif@if(ui.hasMap())<a href="/map">${ui.t("nav.map")}</a>@endif</nav>
    <form class="search" action="/search" method="get" role="search">
        <label class="sr" for="q">${ui.t("search.label")}</label>
        <input id="q" type="search" name="q" placeholder="${ui.t("search.placeholder")}">
    </form>
    @if(account && ui.viewer().offersSignIn())
    <a class="account" href="${ui.viewer().signInUrl()}">${ui.t("signin.link")}</a>
    @elseif(account && ui.viewer().signedIn())
    <form class="account" action="/sign-out" method="post">
        <span class="muted">${ui.viewer().user()}</span>
        <button class="link" type="submit">${ui.t("signout")}</button>
    </form>
    @endif
    @endif
</header>
@if(ui.viewer().membersBar())
<div class="members-bar">${ui.t("members.bar")}</div>
@endif
${content}
<footer class="site">
    <span>${Site.PROGRAM}</span>@if(ui.sourceUrl() != null)<span aria-hidden="true">·</span><a href="${ui.sourceUrl()}">${ui.t("footer.source")}</a>@endif
</footer>
</body>
</html>
