package com.project.finance.service;

import com.project.finance.dto.PortfolioOptionResponse;
import com.project.finance.dto.UserPortfoliosResponse;
import com.project.finance.entity.Portfolio;
import com.project.finance.entity.UserAccount;
import com.project.finance.repository.PortfolioRepository;
import com.project.finance.repository.UserAccountRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PortfolioService {

    private static final String DEFAULT_PORTFOLIO_NAME = "Portfolio";

    private final PortfolioRepository portfolioRepository;
    private final UserAccountRepository userAccountRepository;

    public PortfolioService(
            PortfolioRepository portfolioRepository,
            UserAccountRepository userAccountRepository
    ) {
        this.portfolioRepository = portfolioRepository;
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional
    public UserPortfoliosResponse getUserPortfolios(String username) {
        String normalizedUsername = normalizeUsername(username);

        UserAccount user = userAccountRepository.findByUsernameIgnoreCase(normalizedUsername)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + normalizedUsername));

        List<Portfolio> portfolios = portfolioRepository
                .findByUserUserIdOrderByPortfolioNameAscPortfolioIdAsc(user.getUserId());
        if (portfolios.isEmpty()) {
            Portfolio created = new Portfolio();
            created.setUser(user);
            created.setPortfolioName(DEFAULT_PORTFOLIO_NAME);
            portfolios = List.of(portfolioRepository.save(created));
        }

        List<PortfolioOptionResponse> portfolioResponses = portfolios.stream()
                .map(portfolio -> new PortfolioOptionResponse(
                        portfolio.getPortfolioId(),
                        portfolio.getPortfolioName()
                ))
                .toList();

        return new UserPortfoliosResponse(
                user.getUserId(),
                user.getUsername(),
                portfolioResponses.size(),
                List.copyOf(portfolioResponses)
        );
    }

    private String normalizeUsername(String username) {
        if (!StringUtils.hasText(username)) {
            throw new IllegalArgumentException("username is required.");
        }

        String normalizedUsername = username.trim();
        if (normalizedUsername.length() > 50) {
            throw new IllegalArgumentException("username cannot exceed 50 characters.");
        }
        return normalizedUsername;
    }
}
