import { Routes } from '@angular/router';
import {LoginComponent} from "./page/login/login.component";
import {RegisterComponent} from "./page/register/register.component";
import {ActivateAccountComponent} from "./page/activate-account/activate-account.component";
import {authGuard} from "./services/guard/auth.guard";

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'books',
    pathMatch: 'full'
  },
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
    loadChildren: () => import('./book/book-route').then(r => r.bookRoute),
    canActivate: [authGuard]
  }
];
