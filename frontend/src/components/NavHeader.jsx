import { Link } from 'react-router-dom'
import { VERSION } from '../version'
import './NavHeader.css'

function NavHeader() {
  return (
    <header className="nav-header">
      <div className="nav-header-title">
        <h1>Temperature Monitor</h1>
        <span className="version">v{VERSION.toString()}</span>
      </div>
      <nav className="nav-header-links">
        <Link to="/">Dashboard</Link>
        <Link to="/reporting">Reporting</Link>
      </nav>
    </header>
  )
}

export default NavHeader
