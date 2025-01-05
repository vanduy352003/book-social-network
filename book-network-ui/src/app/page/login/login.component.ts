import {SocialLoginResponse} from "../../services/models/social-login-response";

declare var google: any;
import {Component, OnInit} from '@angular/core';
import {AuthenticationRequest} from "../../services/models/authentication-request";
import {NgForOf, NgIf} from "@angular/common";
import {FormsModule} from "@angular/forms";
import {ActivatedRoute, Router} from "@angular/router";
import {AuthenticationService} from "../../services/services/authentication.service";
import {Token} from "@angular/compiler";
import {TokenService} from "../../services/token/token.service";

//TODO check security when login,... encode data
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    NgForOf,
    FormsModule,
    NgIf
  ],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss'
})
export class LoginComponent implements OnInit {
  private supportedProviers = ['facebook', 'google'];
  private provider: string | undefined;
  authRequest: AuthenticationRequest = {email: '', password: ''};
  errorMsg: Array<string> = [];

  constructor(
    private router: Router,
    private authService: AuthenticationService,
    private tokenService: TokenService,
    private activatedRoute: ActivatedRoute
  ) {
  }

  login() {
    this.errorMsg = [];
    this.authService.authenticate({
      body: this.authRequest
    }).subscribe({
      next: (res) => {
        this.tokenService.token = res.token as string;
        this.router.navigate(['books']);
      },
      error: (err) => {
        console.log(err);
        if (err.error.validationErrors) {
          this.errorMsg = err.error.validationErrors
        } else {
          this.errorMsg.push(err.error.error)
        }
      }
    })
  }

  // loginGoogle2(idToken: string) {
  //   this.errorMsg = [];
  //   this.authService.googleAuthenticate({
  //     body: idToken
  //   }).subscribe({
  //     next: (res) => {
  //       this.tokenService.token = res.token as string;
  //       this.router.navigate(['books']);
  //     },
  //     error: (err) => {
  //       console.log(err);
  //       if (err.error.validationErrors) {
  //         this.errorMsg = err.error.validationErrors
  //       } else {
  //         this.errorMsg.push(err.error.error)
  //       }
  //     }
  //   })
  // }

  register() {
    this.router.navigate(['register'])
  }

  loginWithGoogle() {
    this.loginSocial('google')
  }

  loginWithFacebook() {
    this.loginSocial('facebook')
  }

  private loginSocial(provider: string) {
    this.authService.socialLogin({
      socialName: provider
    }).subscribe({
      next: (value: SocialLoginResponse) => {
        let queryParams = this.getQueryParam(value.url || '');
        localStorage.setItem('state', queryParams['state'])
        console.log(value.url);
        window.location.href = value.url || '';
      },
      error: (err) => {
        console.log(err);
        if (err.error.validationErrors) {
          this.errorMsg = err.error.validationErrors
        } else {
          this.errorMsg.push(err.error.error)
        }
      }
    })
  }

  getQueryParam(url: string): Record<string, string> {
    const queryString = url.split('?')[1] || '';
    const params: Record<string, string> = {}
    console.log('queryString ', queryString);
    queryString.split('&').forEach((param) => {
      console.log('param: ', param)
      const key = param.substring(0, param.indexOf('=')),
        value = param.substring(param.indexOf('=') + 1);
      if (key) {
        console.log(key, ': ', value)
        params[key] = value || '';
      }
    })
    return params;
  }

  ngOnInit(): void {
    let queryParams = this.activatedRoute.snapshot.queryParams;
    let params = this.activatedRoute.snapshot.params;
    let state = localStorage.getItem('state');
    this.provider = params['provider']?.toString().toLowerCase();
    console.log(this.router.url);
    if (this.tokenService.token)
      this.router.navigate(['books'])
    if (!this.provider || !this.supportedProviers.includes(this.provider)
      || decodeURIComponent(state || '') !== queryParams['state']) {
      this.router.navigate(['login']);
      return;
    }
    this.authService.authenticateSocial({
      body: {
        code: queryParams['code'],
        provider: this.provider
      }
    }).subscribe({
      next: (res) => {
        this.tokenService.token = res.token as string;
        this.router.navigate(['books']);
      },
      error: (err) => {
        if (err.error.validationErrors) {
          this.errorMsg = err.error.validationErrors
        } else {
          this.errorMsg.push(err.error.error)
        }
      },
      complete: () => {
        localStorage.removeItem('state');
      }
    })
  }
}
