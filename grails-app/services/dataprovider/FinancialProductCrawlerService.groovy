package dataprovider

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

    def timeoutList = []

    def cityMap =
            [2  : "北京市",
             10 : "上海市",
             279: "深圳市",
             277: "广州市",
             159: "苏州市",
             168: "杭州市",
             328: "成都市",
             250: "武汉市",
             155: "南京市",
             23 : "重庆市",
             3  : "天津市",
             170: "温州市",
             233: "郑州市",
             282: "佛山市",
             293: "东莞市",
             381: "西安市",
             169: "宁波市",
             217: "青岛市",
             329: "自贡市",
             216: "济南市",
             67 : "石家庄市",
             101: "沈阳市",
             196: "福州市",
             263: "长沙市",
             179: "合肥市",
            ]


    def crawlerAllFinancialProduct() {

        def startPage = "http://www.yinhang.com/licai/list/?page=1&ma=100000&pt=51&mt=100&ar=2&mtmin=0"
        cityMap.each { city ->
            startPage = "http://www.yinhang.com/licai/list/?page=1&ma=100000&pt=51&mt=100&ar=" + city.key + "&mtmin=0"
            def maxPageNum = findMaxPage(startPage)
            (1..maxPageNum).each {
                String pageUrl = "http://www.yinhang.com/licai/list/?page=" + it.toString() + "&ma=100000&pt=51&mt=100&ar=" + city.key + "&mtmin=0"
                crawlSinglePage(pageUrl)
            }
        }

        crawlingTimeoutPage(timeoutList)

    }

    def crawlingTimeoutPage(timeoutList) {
        if (timeoutList != null) {
            timeoutList.each { String timeouPage ->
                crawlSinglePage(timeouPage)
                timeoutList - timeouPage
            }
        } else {
            crawlingTimeoutPage(timeoutList)
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

    def crawlSinglePage(String pageUrl) {
        Document pageDocument
        try {
            pageDocument = Jsoup.connect(pageUrl).get()

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
                Pattern patternCityCode = ~/ar=\d+/
                Matcher cityCodeMatcher = patternCityCode.matcher(pageUrl)
                if (cityCodeMatcher.find()) {
                    def cityCode = cityCodeMatcher.group().replace("ar=", "") as int
                    financialProduct.city = cityMap.get(cityCode)
                }
                financialProduct.riskRank = financialProductInfo.select(".ulfour li").html()
                financialProduct.preservationType = financialProductInfo.select(".subtitle").text().split(" ")[0]
                financialProduct.profitType = financialProductInfo.select(".subtitle").text().split(" ")[1]
                saveFinancialProductInfo(financialProduct)
            }
        } catch (IOException e) {
            timeoutList << pageUrl
        }
    }

    def saveFinancialProductInfo(FinancialProduct financialProduct) {

        JSONObject jsonObject = new JSONObject()
        jsonObject.put("city", financialProduct.city)
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
