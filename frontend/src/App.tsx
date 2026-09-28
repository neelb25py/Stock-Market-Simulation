import { BrowserRouter, Routes, Route } from "react-router-dom"
import MarketDashboard from "./components/pages/MarketDashboard/MarketDashboard"
import StockDashboard from "./components/pages/StockDashboard/StockDashboard"
import AdminDashboard from './components/pages/AdminDashboard'
import TeamLogin from './components/pages/TeamLogin'
import CompetitionDashboard from './components/pages/CompetitionDashboard'

function App(){
    return (
        <BrowserRouter>
            <Routes>
                <Route index element={<TeamLogin/>} />
                <Route path="admin" element={<AdminDashboard/>} />
                <Route path="team/login" element={<TeamLogin/>} />
                <Route path="competition/:id/market" element={<CompetitionDashboard/>} />
                <Route path="legacy" element={<MarketDashboard/>} />
                <Route path="stocks/:ticker" element={<StockDashboard/>} />
            </Routes>
        </BrowserRouter>
    )
}

export default App
