import React from "react";
import "../css/Portfolio.css";

const Portfolio = () => {



    return (
        <>
            <div className="portfolioMainContainer">
                <div className="portfolioPreviewContainer">
                    <div className="portfolioValue"> Portfolio Value </div>
                    <div className="portfolioPerformance"> Portfolio performance </div>
                    
                    <div className="portfolioContentContainer">
                        <div className="portfolioGraphContainer">
                            <div className="portfolioGraph"> Portfolio graph </div>
                        </div>

                    <div className="portfolioAssetsContainer">
                        <div className="portfolioAssetsHeader"> Investments </div>
                        <div className="portfolioAssetsListContainer">
                            <div className="portfolioAssetsList"> Asset 1 </div>
                        </div>
                    </div>
                    
                    <div className="portfolioAllocationContainer">
                        <div className="portfolioAllocationHeader"> Allocations </div>
                        <div className="portfolioAllocationListContainer">
                            <div className="portfolioAllocationList"> Allocation 1</div>

                        </div>
                    </div>
                </div>
            </div>
                
            <div className="portfolioDataContainer">
                <div className="portfolioData">This is where the portfolio details will be displayed.</div>
            </div>
        </div>
    )
}

export default Portfolio;