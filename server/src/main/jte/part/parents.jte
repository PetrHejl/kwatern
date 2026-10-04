@import me.hejl.kwatern.view.Pages.ParentsBlock
@import me.hejl.kwatern.web.Ui
@param Ui ui
@param ParentsBlock parents
<div class="d-parents">
    @template.part.box(ui = ui, link = parents.father(), role = ui.t("father"), relation = parents.fatherRelation())
    @template.part.box(ui = ui, link = parents.mother(), role = ui.t("mother"), relation = parents.motherRelation())
</div>
