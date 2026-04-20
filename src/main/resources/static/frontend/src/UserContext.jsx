import React, { createContext, useContext, useState } from 'react';

const UserContext = createContext();

export const UserProvider = ({ children }) => {
    const [user, setUser] = useState({
        isLoggedIn: true,
        username: 'demo',
        name: 'Demo User',
        email: 'demo@example.com'
    });
    const [selectedPortfolioId, setSelectedPortfolioId] = useState(null);

    return (
        <UserContext.Provider value={{ user, setUser, selectedPortfolioId, setSelectedPortfolioId }}>
            {children}
        </UserContext.Provider>
    );
};

export const useUser = () => useContext(UserContext);
