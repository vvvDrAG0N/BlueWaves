#ifndef WEBNOVEL_SCRAPER_H
#define WEBNOVEL_SCRAPER_H

#include "TitanScraper.h"
#include <functional>

/**
 * WebnovelScraper (V2)
 * 
 * A concrete implementation of the TitanScraper for reading text-based novels.
 */
class WebnovelScraper : public TitanScraper {
public:
    // A callback to fetch HTML from the network (Kotlin layer)
    using FetchCallback = std::function<std::string(const std::string& url)>;

    WebnovelScraper(FetchCallback fetcher) : m_fetcher(fetcher) {}

    std::vector<ScraperResult> search(const std::string& query) override;

private:
    FetchCallback m_fetcher;
};

#endif // WEBNOVEL_SCRAPER_H
