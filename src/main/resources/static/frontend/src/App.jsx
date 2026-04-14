import React from 'react'
import { Routes, Route } from 'react-router-dom'
import NavBar from './Components/NavBar'
import Dashboard from './pages/Dashboard'


const App = () => {
    return (
    <>
      <div className="pageLayout" style={{"height": "100%"}}>
        <NavBar />
        <div className="main">
          <Routes>
            <Route exact path="/" element={<Dashboard />} />
          </Routes>
        </div>
      </div>
    </>
  )
}

export default App;
