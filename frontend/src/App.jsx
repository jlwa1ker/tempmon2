import { Routes, Route } from 'react-router-dom'
import NavHeader from './components/NavHeader'
import Dashboard from './components/Dashboard'
import ReportingPage from './components/ReportingPage'
import './App.css'

function App() {
  return (
    <div className="App">
      <NavHeader />
      <Routes>
        <Route path="/" element={<Dashboard />} />
        <Route path="/reporting" element={<ReportingPage />} />
      </Routes>
    </div>
  )
}

export default App
