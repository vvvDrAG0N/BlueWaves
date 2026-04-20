#ifndef TITAN_SCRAPER_H
#define TITAN_SCRAPER_H

#include <string>
#include <vector>

/**
 * TitanScraper (V2)
 * 
 * The base architecture for our volumetric scrapers.
 * This can be extended to support Manga, Anime, and Music.
 */

struct ScraperResult {
    std::string title;
    std::string url;
    std::string coverUrl;
    std::string source;
};

class TitanScraper {
public:
    virtual ~TitanScraper() = default;

    /**
     * Searches for content using the specific scraper logic.
     * @param query The search query.
     * @return A vector of results.
     */
    virtual std::vector<ScraperResult> search(const std::string& query) = 0;
};

#endif // TITAN_SCRAPER_H
