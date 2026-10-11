@import java.util.List
@import me.hejl.kwatern.view.Pages.Piece
@param List<Piece> pieces
@for(var p : pieces)@if(p.style().isEmpty())${p.text()}@else<span class="${p.style()}">${p.text()}</span>@endif@endfor