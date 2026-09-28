import type { Competition, LeaderboardRow, Market, News, Portfolio, PricePoint, Stock, Team, TeamCreated } from '../types/Competition'
const API = import.meta.env.VITE_API_URL ?? 'http://localhost:8000/api'
async function request<T>(path:string, init:RequestInit={}, token?:string, adminKey?:string):Promise<T>{
  const headers=new Headers(init.headers); if(!(init.body instanceof FormData))headers.set('Content-Type','application/json'); if(token)headers.set('Authorization',`Bearer ${token}`); if(adminKey)headers.set('X-Admin-Key',adminKey)
  const response=await fetch(`${API}${path}`,{...init,headers}); if(!response.ok){const payload=await response.json().catch(()=>({message:response.statusText}));throw new Error(payload.message??response.statusText)}
  if(response.status===204||response.headers.get('content-length')==='0')return undefined as T; return response.json() as Promise<T>
}
export const adminApi={
  list:(key:string)=>request<Competition[]>('/admin/competitions',{},undefined,key),
  create:(key:string,body:object)=>request<Competition>('/admin/competitions',{method:'POST',body:JSON.stringify(body)},undefined,key),
  get:(key:string,id:number)=>request<Competition>(`/admin/competitions/${id}`,{},undefined,key),
  stocks:(key:string,id:number)=>request<Stock[]>(`/admin/competitions/${id}/stocks`,{},undefined,key),
  defaults:(key:string,id:number)=>request<Stock[]>(`/admin/competitions/${id}/stocks/defaults`,{method:'POST'},undefined,key),
  fetchPrices:(key:string,id:number)=>request<{updated:number;preserved:number;errors:unknown[]}>(`/admin/competitions/${id}/stocks/fetch-prices`,{method:'POST'},undefined,key),
  teams:(key:string,id:number)=>request<Team[]>(`/admin/competitions/${id}/teams`,{},undefined,key),
  createTeams:(key:string,id:number,names:string[],budget:number)=>request<TeamCreated[]>(`/admin/competitions/${id}/teams/bulk`,{method:'POST',body:JSON.stringify({names,startingBudget:budget})},undefined,key),
  generate:(key:string,id:number)=>request<{points:number}>(`/admin/competitions/${id}/reference/generate`,{method:'POST'},undefined,key),
  createNews:(key:string,id:number,body:object)=>request<News>(`/admin/competitions/${id}/news`,{method:'POST',body:JSON.stringify(body)},undefined,key),
  news:(key:string,id:number)=>request<News[]>(`/admin/competitions/${id}/news`,{},undefined,key),
  action:(key:string,id:number,action:string)=>request<Competition>(`/admin/competitions/${id}/${action}`,{method:'POST'},undefined,key),
  leaderboard:(key:string,id:number)=>request<LeaderboardRow[]>(`/admin/competitions/${id}/leaderboard`,{},undefined,key),
}
export const teamApi={
  login:(competitionId:number,teamName:string,accessCode:string)=>request<{token:string;competitionId:number;teamId:number;teamName:string}>('/team/login',{method:'POST',body:JSON.stringify({competitionId,teamName,accessCode})}),
  market:(id:number,token:string)=>request<Market>(`/competitions/${id}/market`,{},token),
  portfolio:(id:number,token:string)=>request<Portfolio>(`/competitions/${id}/portfolio`,{},token),
  history:(id:number,ticker:string,token:string)=>request<PricePoint[]>(`/competitions/${id}/stocks/${encodeURIComponent(ticker)}/history`,{},token),
  order:(id:number,side:'buy'|'sell',ticker:string,quantity:number,token:string)=>request(`/competitions/${id}/orders/${side}`,{method:'POST',body:JSON.stringify({ticker,quantity})},token),
  eventUrl:(id:number,token:string)=>`${API}/competitions/${id}/events?token=${encodeURIComponent(token)}`,
}
