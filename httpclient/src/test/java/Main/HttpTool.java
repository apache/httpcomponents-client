package Main;

import java.io.IOException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

/**
 * http 工具
 */
public class HttpTool {
    
//    private static Logger logger = LoggerFactory.getLogger(HttpTool.class);
    
    private static String ENCODING = "UTF-8";
    
    private static String CONTENT_TYPE = "application/json";
    
    /**
     * 发送POST请求，参数是JSON
     */
    public static String requestPost(String url, String jsonParam){
//        logger.info("HttpTool.requestPost 开始 请求url：" + url + ", 参数：" + jsonParam);
        //创建HttpClient对象
        CloseableHttpClient client = HttpClients.createDefault();
        //创建HttpPost对象
        HttpPost httpPost = new HttpPost(url);
        
        //配置请求参数
        RequestConfig requestConfig = RequestConfig.custom()
                .setCookieSpec(CookieSpecs.DEFAULT)
                .setExpectContinueEnabled(true)
                .setSocketTimeout(5000)  
                .setConnectTimeout(5000)  
                .setConnectionRequestTimeout(5000)  
                .build();
        
        httpPost.setConfig(requestConfig);
        
        String respContent = null;
            
        //设置参数和请求方式
        StringEntity entity = new StringEntity(jsonParam,ENCODING);//解决中文乱码问题    
        entity.setContentEncoding(ENCODING);    
        entity.setContentType(CONTENT_TYPE);  
        
        httpPost.setEntity(entity);
            
        HttpResponse resp;
        try {
            //执行请求
            resp = client.execute(httpPost);
            if(resp.getStatusLine().getStatusCode() == 200) {
                HttpEntity responseObj = resp.getEntity();
                respContent = EntityUtils.toString(responseObj,"UTF-8");
            }
        } catch (IOException e) {
            e.printStackTrace();
//            logger.info("HttpTool.requestPost 异常 请求url：" + url + ", 参数：" + jsonParam + "，异常信息：" + e);
        }
//        logger.info("HttpTool.requestPost 结束 请求url：" + url + ", 参数：" + jsonParam + "");
        //返回数据
        return respContent;
    }
}
