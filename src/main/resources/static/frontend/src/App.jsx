import React from 'react'
import { Routes, Route } from 'react-router-dom'
import NavBar from './Components/NavBar'
import Dashboard from './pages/Dashboard'
import Portfolio from './pages/Portfolio'


const App = () => {
    return (
    <>
      <div className="pageLayout" style={{"height": "100%"}}>
        <NavBar />
        <div className="main">
          <Routes>
            <Route exact path="/" element={<Dashboard />} />
            <Route path="/portfolio" element={<Portfolio />} />
          </Routes>
        </div>
      </div>
    </>
  )
}

export default App;
