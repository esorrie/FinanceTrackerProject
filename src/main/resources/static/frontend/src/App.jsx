import React from 'react'
import { Link, Routes, Route } from 'react-router-dom'
import Dashboard from './pages/Dashboard'

export default function App() {
  return (
    <div className="app">
      <nav className="nav">
        <Link to="/">Dashboard</Link>
      </nav>
      <main className="main">
        <Routes>
          <Route path="/" element={<Dashboard />} />
        </Routes>
      </main>
    </div>
  )
}
