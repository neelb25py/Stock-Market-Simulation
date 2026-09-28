import { Line } from 'react-chartjs-2'
import { CategoryScale, Chart as ChartJS, Filler, Legend, LinearScale, LineElement, PointElement, Tooltip } from 'chart.js'
import type { PricePoint } from '../../types/Competition'
ChartJS.register(CategoryScale,LinearScale,PointElement,LineElement,Tooltip,Legend,Filler)
export default function StockPriceChart({points,ticker}:{points:PricePoint[];ticker:string}){
  return <div className="chart-panel"><Line data={{labels:points.map(p=>`${p.secondOffset}s`),datasets:[{label:ticker,data:points.map(p=>p.price),borderColor:'#22c55e',backgroundColor:'rgba(34,197,94,.12)',pointRadius:0,fill:true,tension:.22}]}} options={{responsive:true,maintainAspectRatio:false,animation:false,plugins:{legend:{display:false}},scales:{x:{ticks:{maxTicksLimit:8,color:'#8491a8'},grid:{display:false}},y:{ticks:{color:'#8491a8'},grid:{color:'rgba(148,163,184,.1)'}}}}}/></div>
}
