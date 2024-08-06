import {Routes} from "@angular/router";
import {BookComponent} from "./book.component";
import {BookListComponent} from "./pages/book-list/book-list.component";

export const bookRoute: Routes = [
  {
    path: '',
    component: BookComponent,
    children: [
      {
        path: '',
        component: BookListComponent
      }
    ]
  }
];
