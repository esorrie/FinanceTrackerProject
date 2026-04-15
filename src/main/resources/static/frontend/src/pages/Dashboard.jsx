import React from 'react';
import '../css/Dashboard.css';

const Dashboard = () => {
    return (
        <div className="dashboard">
            <div className="dashPortfolioContainer">
                <div className="dashPortfolioHeader"> 
                    <div>Portfolio</div>
                </div>
                <div className='dashPortfolioData'>
                    <div>Placeholder for portfolio value</div>
                    <div>Placeholder for profit/loss</div>
                </div>
            </div>
            <div className='otherDash'>
                <div className='topWinners'>Placeholder for portfolio value</div>
                <div className='topLosers'>Placeholder for profit/loss</div>
            </div>
        </div>
    )
}

export default Dashboard;