package dataprovider

import grails.converters.JSON
import grails.transaction.Transactional
import net.sf.json.JSONObject
import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.protocol.HTTP
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

import java.util.regex.Matcher
import java.util.regex.Pattern

@Transactional
class FinancialProductCrawlerService {

    def crawlerAllFinancialProduct() {
        def startPage = "http://www.yinhang.com/licai/list/?page=1&ma=100000&pt=51&mt=100&ar=2&mtmin=0"
        def maxPageNum = findMaxPage(startPage)
        (1..maxPageNum).each {
            String pageUrl = "http://www.yinhang.com/licai/list/?page=" + it.toString() + "&ma=100000&pt=51&mt=100&ar=2&mtmin=0"
            crawlSinglaPage(pageUrl)
        }
    }

    def findMaxPage(String startPage) {
        Document startPageDocument = Jsoup.connect(startPage).get()
        def maxPageUrl = startPageDocument.select(".page a:last-child").attr("href")
        Pattern pattern = ~/page=\d+/
        Matcher matcher = pattern.matcher(maxPageUrl)
        def maxPageNum
        if (matcher.find()) {
            maxPageNum = matcher.group().replace("page=", "")
        }
        return maxPageNum as int
    }

    def crawlSinglaPage(String pageUrl) {
        Document pageDocument = Jsoup.connect(pageUrl).get()
        def financialProductInfoList = pageDocument.select(".result-list").select("tr")
        financialProductInfoList.each { financialProductInfo ->
            FinancialProduct financialProduct = new FinancialProduct()
            financialProduct.issuer = financialProductInfo.select("p").html()
            financialProduct.productName = financialProductInfo.select("td .adata .title").text()
            def basicFinancialProductInfo = financialProductInfo.select("td .info .ulone li")
            Pattern patternInt = ~/\d+/
            Matcher minAmountOfInvestmentMatcher = patternInt.matcher(basicFinancialProductInfo[0].html())
            if (minAmountOfInvestmentMatcher.find()) {
                financialProduct.minAmountOfInvestment = minAmountOfInvestmentMatcher.group() as int
            }
            Matcher investmentTimeMatcher = patternInt.matcher(basicFinancialProductInfo[1].html())
            if (investmentTimeMatcher.find()) {
                financialProduct.investmentTime = investmentTimeMatcher.group() as int
            }
            Pattern patternFloat = ~/\d+\.\d+/
            Matcher expectedYieldMatcher = patternFloat.matcher(basicFinancialProductInfo[2].html())
            if (expectedYieldMatcher.find()) {
                financialProduct.expectedYield = expectedYieldMatcher.group() as float
            }
            Pattern patternDate = ~/\d{4}-\d{2}-\d{2}/
            Matcher startDayMatcher = patternDate.matcher(basicFinancialProductInfo[4].html())
            Matcher endDayMatcher = patternDate.matcher(basicFinancialProductInfo[5].html())
            if (startDayMatcher.find() && endDayMatcher.find()) {
                financialProduct.startDate = Date.parse("yyyy-MM-dd", startDayMatcher.group())
                financialProduct.endDate = Date.parse("yyyy-MM-dd", endDayMatcher.group())
            }
            financialProduct.riskRank = financialProductInfo.select(".ulfour li").html()
            financialProduct.preservationType = financialProductInfo.select(".subtitle").text().split(" ")[0]
            financialProduct.profitType = financialProductInfo.select(".subtitle").text().split(" ")[1]
            saveFinancialProductInfo(financialProduct)
        }
    }

    def saveFinancialProductInfo(FinancialProduct financialProduct) {

        JSONObject jsonObject = new JSONObject()
        jsonObject.put("productName", financialProduct.productName)
        jsonObject.put("issuer", financialProduct.issuer)
        jsonObject.put("minAmountOfInvestment", financialProduct.minAmountOfInvestment)
        jsonObject.put("investmentTime", financialProduct.investmentTime)
        println(financialProduct.expectedYield)
        jsonObject.put("expectedYield", Double.valueOf(financialProduct.expectedYield.toString()))
        jsonObject.put("riskRank", financialProduct.riskRank)
        jsonObject.put("preservationType", financialProduct.preservationType)
        jsonObject.put("profitType", financialProduct.profitType)
        def startDate = financialProduct.startDate.format("YYYY-MM-dd'T'HH:mm:ss.SSS'Z'", TimeZone.getTimeZone("UTC"))
        def endDate = financialProduct.endDate.format("YYYY-MM-dd'T'HH:mm:ss.SSS'Z'", TimeZone.getTimeZone("UTC"))
        jsonObject.startDate = ["__type": "Date", "iso": startDate]
        jsonObject.endDate = ["__type": "Date", "iso": endDate]

        HttpPost httpPost = new HttpPost("https://cn.avoscloud.com:443/1.1/classes/FinancialProductInfo");
        StringEntity entity = new StringEntity(jsonObject.toString(), HTTP.UTF_8);
        entity.setContentType("application/json");
        httpPost.setEntity(entity);
        httpPost.setHeader("X-AVOSCloud-Application-Id", "543eanv3de6r242jk51cwzln3dqe04nuj0b87n3z05nqqcgj")
        httpPost.setHeader("X-AVOSCloud-Application-Key", "mg0hbw40y1et96af4y9ppanu1etzn0y33aohiw4t5fk02emr")
        HttpClient client = new DefaultHttpClient();
        HttpResponse response = client.execute(httpPost);
    }
}
