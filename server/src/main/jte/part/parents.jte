@import me.hejl.kwatern.view.Pages.ParentsBlock
@import me.hejl.kwatern.web.Ui
@param Ui ui
@param ParentsBlock parents
<div class="d-parents">
    @template.part.box(ui = ui, link = parents.father(), role = parents.fatherRole(), relation = "")
    @template.part.box(ui = ui, link = parents.mother(), role = parents.motherRole(), relation = "")
</div>
