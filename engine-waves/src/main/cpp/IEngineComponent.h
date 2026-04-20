#ifndef BLUE_WAVES_LEGO_H
#define BLUE_WAVES_LEGO_H

#include <string>
#include <vector>

/**
 * Blue Waves Lego Architecture
 * Every component is an interchangeable interface.
 */

// 1. The Source Lego (Where does the data come from?)
class IDocumentSource {
public:
    virtual ~IDocumentSource() {}
    virtual bool open(const std::string& path) = 0;
    virtual std::string getRawContent(const std::string& entryId) = 0;
};

// 2. The Layout Lego (How does it flow?)
class ILayoutEngine {
public:
    virtual ~ILayoutEngine() {}
    virtual void reflow(const std::string& content, float width) = 0;
};

// 3. The Scraper Lego (How do we get remote content?)
struct ScrapeResult {
    std::string title;
    std::string content;
    std::string nextUrl;
};

class IScraper {
public:
    virtual ~IScraper() {}
    virtual std::vector<ScrapeResult> search(const std::string& query) = 0;
    virtual ScrapeResult fetchChapter(const std::string& url) = 0;
};

// 4. The Core Hub (The Lego Baseplate)
class EngineCore {
public:
    IDocumentSource* source = nullptr;
    ILayoutEngine* layout = nullptr;
    IScraper* scraper = nullptr;
    
    void setSource(IDocumentSource* s) { source = s; }
    void setLayout(ILayoutEngine* l) { layout = l; }
    void setScraper(IScraper* s) { scraper = s; }
};

#endif // BLUE_WAVES_LEGO_H
