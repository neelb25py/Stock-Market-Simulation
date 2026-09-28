export type Status = 'DRAFT' | 'READY' | 'LOCKED' | 'RUNNING' | 'PAUSED' | 'FINISHED'
export interface Competition { id:number; name:string; status:Status; durationSeconds:number; tickIntervalMs:number; defaultStartingBudget:number; randomSeed:number; elapsedSeconds:number; remainingSeconds:number; lockHash?:string; stockCount:number; teamCount:number }
export interface Stock { id:number; ticker:string; companyName:string; sector:string; marketCap:string; startingPrice:number; livePrice:number; changePercent:number; enabled:boolean }
export interface TeamCreated { id:number; name:string; accessCode:string; startingBudget:number }
export interface Team { id:number; name:string; startingBudget:number; cashBalance:number; active:boolean }
export interface Holding { ticker:string; quantity:number; averagePurchasePrice:number; livePrice:number; marketValue:number; realizedPnL:number; unrealizedPnL:number }
export interface Portfolio { teamId:number; teamName:string; startingBudget:number; cashBalance:number; holdingsMarketValue:number; totalPortfolioValue:number; totalPnL:number; returnPercent:number; holdings:Holding[] }
export interface LeaderboardRow { rank:number; teamId:number; teamName:string; startingBudget:number; cash:number; totalPortfolioValue:number; profitLoss:number; returnPercent:number }
export interface News { id:number; headline:string; description:string; releaseSecond:number; releasedAt?:string }
export interface PricePoint { secondOffset:number; price:number; referenceReturn:number; newsReturn:number; participantReturn:number; combinedReturn:number }
export interface Market { competition:Competition; stocks:Stock[]; news:News[]; leaderboard:LeaderboardRow[] }
