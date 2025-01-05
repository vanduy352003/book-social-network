/* tslint:disable */
/* eslint-disable */
import { HttpClient, HttpContext, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { StrictHttpResponse } from '../../strict-http-response';
import { RequestBuilder } from '../../request-builder';

import { SocialLoginResponse } from '../../models/social-login-response';

export interface SocialLogin$Params {
  socialName: string;
}

export function socialLogin(http: HttpClient, rootUrl: string, params: SocialLogin$Params, context?: HttpContext): Observable<StrictHttpResponse<SocialLoginResponse>> {
  const rb = new RequestBuilder(rootUrl, socialLogin.PATH, 'get');
  if (params) {
    rb.query('socialName', params.socialName, {});
  }

  return http.request(
    rb.build({ responseType: 'json', accept: 'application/json', context })
  ).pipe(
    filter((r: any): r is HttpResponse<any> => r instanceof HttpResponse),
    map((r: HttpResponse<any>) => {
      return r as StrictHttpResponse<SocialLoginResponse>;
    })
  );
}

socialLogin.PATH = '/auth/social-login';
