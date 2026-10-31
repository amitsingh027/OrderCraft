import {Injectable} from '@angular/core';

export type Role = 'ADMIN' | 'SALES' | 'PRODUCTION' | 'ACCOUNTS';
export type User = {username:string; role:Role};
export type Order = {id:number; customer:string; product:string; qty:number; price:number; due:string; status:string; paid:number; bom:boolean; po:boolean; invoiced:boolean};
export type Payment = {id:number; amount:number; reference:string|null; recordedBy:string|null; recordedAt:string};
export type AuditEvent = {id:number; orderId:number; action:string; actor:string|null; recordedAt:string};

export class ApiError extends Error {
  constructor(public status:number, message:string) { super(message); }
}

@Injectable({providedIn:'root'})
export class ApiClient {
  private csrf: {token:string;headerName:string}|null = null;

  private async send<T>(path:string,init:RequestInit={}):Promise<T> {
    let response:Response;
    try {
      response=await fetch('/api'+path,{
        ...init,credentials:'same-origin',cache:'no-store',signal:AbortSignal.timeout(15000)
      });
    } catch {
      throw new ApiError(0,'The server did not respond. A submitted payment may still have been recorded. Reconnect and retry with the same reference.');
    }
    if (!response.ok) {
      let message=response.status===401?'Please sign in to continue.':'The request could not be completed.';
      try { const body=await response.json(); if(typeof body.message==='string')message=body.message; } catch {}
      if(response.status===401||response.status===403)this.csrf=null;
      throw new ApiError(response.status,message);
    }
    if(response.status===204)return undefined as T;
    if(!response.headers.get('content-type')?.includes('application/json'))
      throw new ApiError(0,'The database service is unavailable. Start the backend, then reconnect.');
    try { return await response.json() as T; }
    catch { throw new ApiError(0,'The server returned an unreadable response. Reconnect before continuing.'); }
  }

  get<T>(path:string):Promise<T>{return this.send<T>(path);}
  private async headers():Promise<Record<string,string>> {
    this.csrf??=await this.get('/auth/csrf');
    return {[this.csrf!.headerName]:this.csrf!.token};
  }
  async post<T>(path:string,body:unknown={},requestKey?:string):Promise<T> {
    const headers=await this.headers();
    headers['Content-Type']='application/json';
    if(requestKey)headers['Idempotency-Key']=requestKey;
    return this.send<T>(path,{method:'POST',headers,body:JSON.stringify(body)});
  }
  async login(username:string,password:string):Promise<void> {
    const headers=await this.headers();
    headers['Content-Type']='application/x-www-form-urlencoded';
    await this.send('/auth/login',{method:'POST',headers,body:new URLSearchParams({username,password}).toString()});
    this.csrf=null;
  }
  async logout():Promise<void> {
    await this.post('/auth/logout');
    this.csrf=null;
  }
}
