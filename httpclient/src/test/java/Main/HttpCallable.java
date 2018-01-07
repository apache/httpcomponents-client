package Main;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


/**
 * 异步执行HTTP请求
 */
public class HttpCallable implements Callable<String> {
    
    private String url;
    
    private String jsonParams;
    
    public HttpCallable(String url, String jsonParams){
        this.url = url;
        this.jsonParams = jsonParams;
    }

    /**
     * 执行并返回结果
     */
    @Override
    public String call() throws Exception {
        return HttpTool.requestPost(url, jsonParams);
    }
    
    /**
     * 模拟并发测试
     */
    public static void main(String[] args) throws InterruptedException, ExecutionException {
        //模拟并发数
        int concurrencyNumber = 1000;
        
        List<String>  resList = new ArrayList<String>();
        
        //执行线程池
        ExecutorService ex = Executors.newFixedThreadPool(100);
        
        String url = "www.baidu.com";
        Map<String, String> mapPatam = new HashMap<String,String>();
        mapPatam.put("name", "测试");
        
        String params = "{\"name\":\"测试\"}";
        for(int i =0 ; i< concurrencyNumber; i++){
            Future<String> callRes = ex.submit(new HttpCallable(url,params));
            resList.add(i + ">>>" + callRes.get());
        }
        
        for(String s : resList){
            System.out.println("------>" + s);
        }
    }

}
