import { Routes } from '@angular/router';
import {LoginComponent} from "./page/login/login.component";
import {RegisterComponent} from "./page/register/register.component";
import {ActivateAccountComponent} from "./page/activate-account/activate-account.component";

export const routes: Routes = [
  {
    path: 'login',
    component: LoginComponent
  },
  {
    path: 'register',
    component: RegisterComponent
  },
  {
    path: 'activate-account',
    component: ActivateAccountComponent
  },
  {
    path: 'books',
    loadChildren: () => import('./book/book-route').then(r => r.bookRoute)
  }
];
