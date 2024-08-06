import { Component } from '@angular/core';
import {RouterOutlet} from "@angular/router";
import {MenuComponent} from "./components/menu/menu.component";

@Component({
  selector: 'app-book',
  standalone: true,
  imports: [RouterOutlet, MenuComponent],
  templateUrl: './book.component.html',
  styleUrl: './book.component.scss'
})
export class BookComponent {

}
