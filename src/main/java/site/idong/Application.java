package site.idong;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import io.github.itning.retry.Retryer;
import io.github.itning.retry.RetryerBuilder;
import io.github.itning.retry.strategy.stop.StopStrategies;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.cookie.StandardCookieSpec;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.cookie.BasicClientCookie;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Application {
    @Parameter(names={"--user"},help = true,required =true ,description = "�û���¼��Ϣ��JSON��ʽ��")
    String user;
    @Parameter(names = "--help", help = true,description = "����")
    private boolean help;
    private static final Logger log = LoggerFactory.getLogger(Application.class);
    private static String ua="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.55 Safari/537.36 Edg/96.0.1054.43";
    protected static String initVector = "encryptionIntVec";
    static {
        Security.addProvider(new BouncyCastleProvider());
    }
    public static void main(String[] args) {
        Application main = new Application();
        JCommander jct = JCommander.newBuilder()
                .addObject(main)
                .build();
        jct.setProgramName("�Ӻ���ѧ�����ϱ�ϵͳ");
        try {
            jct.parse(args);
            // �ṩ����˵��
            if (main.help) {
                jct.usage();
                return;
            }
            main.run();
        }
        catch (ParameterException parameterException ){
            // Ϊ�˷���ʹ�ã�ͬʱ���exception��message
            System.out.printf(parameterException.toString()+"\r\n");
            jct.usage();
        }
    }
    public void run() {
        Boolean isAllSuccess = true;
        try {
            JSONArray users = JSON.parseArray(user);
            Iterator iter = users.iterator();
            while (iter.hasNext()) {

                JSONObject _user = (JSONObject) iter.next();
                log.info("�����ɹ����û�����" + _user.getString("username"));
                Retryer<Integer> retryer = RetryerBuilder.<Integer>newBuilder()
                        .retryIfResult(result -> result == -1)
                        // �������ִ�д���3��
                        .withStopStrategy(StopStrategies.stopAfterAttempt(3)).build();
                try {
                    retryer.call(() -> doReport(_user.getString("username"), _user.getString("password")));
                } catch (Exception e) {
                    log.error("���Խ������쳣��" + e.getMessage());
                    isAllSuccess = false;
                }
            }
            if (isAllSuccess != Boolean.TRUE) {
                throw new Exception("���ִ�δ�ɹ�");
            }
        } catch (Exception e){
            System.exit(0);
        }
        System.exit(0);
    }
    protected static String encrypt(String value,String key) {
        value = RandomStringUtils.randomAlphabetic(64)+value;
        try {
            IvParameterSpec iv = new IvParameterSpec(initVector.getBytes("UTF-8"));
            SecretKeySpec skeySpec = new SecretKeySpec(key.getBytes("UTF-8"), "AES");

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec, iv);

            byte[] encrypted = cipher.doFinal(value.getBytes());
            return Base64.encodeBase64String(encrypted);
        } catch (Exception ex) {
            log.error(ex.toString());
        }
        return null;
    }
    protected static int doReport(String username, String password){
        final ExecutorService exec = Executors.newFixedThreadPool(1);
        Callable<Integer> call = new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                Code code = newReport(username,password);
                if(code == Code.CHANGE_EHHU) {
                    log.info("�����л���E�Ӻ��ӿڴ�");
                    code = oldReport(username, password);
                    if(code == Code.EXIT){
                        log.error("��ʧ��");
                        return 0;
                    }
                    if(code == Code.RETRY){
                        log.error("׼������");
                        return -1;
                    }
                    else if(code == Code.OK){
                        log.info("�򿨳ɹ�");
                        return 0;
                    }
                }
                else if(code == Code.EXIT){
                    log.error("��ʧ��");
                    return 0;
                }
                if(code == Code.RETRY){
                    log.error("׼������");
                    return -1;
                }
                else if(code == Code.OK){
                    log.info("�򿨳ɹ�");
                    return 0;
                }
                return 0;
            }
        };
        Future<Integer> future = exec.submit(call);
        try {
            return future.get(1000 * 180, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            exec.shutdownNow();
            return -1;
        } catch (InterruptedException e) {
            return -1;
        } catch (ExecutionException e) {
            return -1;
        }
    }
    public static List<String> regEx(String patten, String textArea) {
        String pattern = patten;
        Pattern compile = Pattern.compile(pattern);
        Matcher matcher = compile.matcher(textArea);
        List<String> targetList = new ArrayList<String>();
        while (matcher.find()) {
            String substring = textArea.substring(matcher.start(), matcher.end());
            targetList.add(substring);
        }
        return targetList;
    }
    private static Code oldReport(String username, String password) {
        // ȫ����������
        RequestConfig globalConfig = RequestConfig.custom().setCookieSpec(StandardCookieSpec.STRICT).setCircularRedirectsAllowed(true).build();
        // ����cookie store�ı���ʵ��
        CookieStore cookieStore =  new BasicCookieStore();
        // ����HttpClient������
        HttpClientContext context = HttpClientContext.create();
        context.setCookieStore(cookieStore);

        // ����һ��HttpClient
        CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(globalConfig)
                .setDefaultCookieStore(cookieStore).build();

        CloseableHttpResponse res =  null ;
        log.info("����ʹ��E�Ӻ���");
        HttpPost httpPost = new HttpPost("http://mids.hhu.edu.cn/_ids_mobile/login18_9");
        List<NameValuePair> nvps = new ArrayList<>();
        nvps.add(new BasicNameValuePair("username", username));
        nvps.add(new BasicNameValuePair("password", password));
        httpPost.setEntity(new UrlEncodedFormEntity(nvps));
        try {
            res = httpClient.execute(httpPost,context);
            if(res.getFirstHeader("loginErrCode") != null){
                log.error("�û�����������󣬴�����룺"+res.getFirstHeader("loginErrCode").getValue());
                return Code.EXIT;
            }
            else{

                if(res.getFirstHeader("ssoCookie") != null) {
                    JSONArray cookieArr = JSONArray.parseArray(res.getFirstHeader("ssoCookie").getValue());
                    Iterator<Object> it   = cookieArr.iterator();
                    while (it.hasNext()) {
                        JSONObject jsonObj = (JSONObject) it.next();
                        BasicClientCookie cookie = new BasicClientCookie(jsonObj.getString("cookieName"), jsonObj.getString("cookieValue"));
                        cookie.setDomain("form.hhu.edu.cn");
                        cookieStore.addCookie(cookie);
                    }
                    log.info("E�Ӻ���¼�ɹ�");
                    res.close();
                    HttpGet httpGet = new HttpGet("http://form.hhu.edu.cn/pdc/form/list");
                    res = httpClient.execute(httpGet,context);
                    String page = null;
                    try {
                        page = EntityUtils.toString(res.getEntity());
                    } catch (ParseException e) {
                        log.error(e.toString());
                        return Code.RETRY;
                    }
                    if(page.contains("������")){
                        if(page.contains("������")){
                            res.close();
                            log.info("form.hhu.edu.cnʶ��ɹ�����ݣ�������");
                            httpGet = new HttpGet("http://form.hhu.edu.cn/pdc/formDesignApi/S/gUTwwojq");
                            res = httpClient.execute(httpGet,context);
                            try {
                                page = EntityUtils.toString(res.getEntity());
                            } catch (ParseException e) {
                                log.error(e.toString());
                                return Code.RETRY;
                            }
                            if(page.contains("δ֪����")){
                                log.error("form.hhu.edu.cnϵͳ�쳣");
                                return Code.RETRY;
                            }
                            String wid = regEx("(?<=_selfFormWid = \\')(.*?)(?=\\')", page).get(0);
                            String uid = regEx("(?<=_userId = \\')(.*?)(?=\\')", page).get(0);
                            String fillDetail = regEx("(?<=fillDetail = )(.*?)(?=\\;)", page).get(0);
                            String json = "{\"XGH_336526\": \"ѧ��\",\"XM_1474\": \"����\",\"SFZJH_859173\": \"���֤��\",\"SELECT_941320\": \"ѧԺ\",\"SELECT_459666\": \"�꼶\",\"SELECT_814855\": \"רҵ\",\"SELECT_525884\": \"�༶\",\"SELECT_125597\": \"����¥\",\"TEXT_950231\": \"�����\",\"TEXT_937296\": \"�ֻ���\",\"RADIO_6555\": \"�������������\",\"RADIO_535015\": \"�������Ƿ���У��\",\"RADIO_891359\": \"���˽��������\",\"RADIO_372002\": \"ͬס�˽��������\",\"RADIO_618691\": \"���˼�ͬס��14�����Ƿ����и߷��յ����þ�ʷ��Ӵ����и߷��յ�����Ա��\"}";
                            JSONObject col = JSON.parseObject(json);
                            JSONArray fills = JSON.parseArray(fillDetail);
                            JSONObject fill = (JSONObject) fills.get(0);
                            SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd");
                            Date date = new Date(System.currentTimeMillis());

                            Iterator iter = col.entrySet().iterator();
                            List<NameValuePair> post = new ArrayList<>();
                            post.add(new BasicNameValuePair("DATETIME_CYCLE", formatter.format(date)));
                            while (iter.hasNext()) {
                                Map.Entry entry = (Map.Entry) iter.next();
                                post.add(new BasicNameValuePair(entry.getKey().toString(), fill.getString(entry.getKey().toString())));
                            }
                            httpPost = new HttpPost("http://form.hhu.edu.cn/pdc/formDesignApi/dataFormSave?wid="+wid+"&userId="+uid);
                            httpPost.setEntity(new UrlEncodedFormEntity(post, StandardCharsets.UTF_8));
                            res = httpClient.execute(httpPost,context);
                            try {
                                if(EntityUtils.toString(res.getEntity()).equals("{\"result\":true}")){
                                    log.info("�򿨳ɹ�");
                                    iter = col.entrySet().iterator();
                                    while (iter.hasNext()) {
                                        Map.Entry entry = (Map.Entry) iter.next();
                                        log.info(entry.getValue()+":"+fill.getString(entry.getKey().toString()));
                                    }
                                    return Code.OK;
                                }
                                else{
                                    log.error("��ʧ��");
                                    return Code.RETRY;
                                }
                            } catch (ParseException e) {
                                log.error(e.toString());
                                return Code.RETRY;
                            }
                        }
                        else if(page.contains("�о���")){
                            res.close();
                            log.info("form.hhu.edu.cnʶ��ɹ�����ݣ��о���");
                            httpGet = new HttpGet("http://form.hhu.edu.cn/pdc/formDesignApi/S/xznuPIjG");
                            res = httpClient.execute(httpGet,context);
                            try {
                                page = EntityUtils.toString(res.getEntity());
                            } catch (ParseException e) {
                                log.error(e.toString());
                                return Code.RETRY;
                            }
                            if(page.contains("δ֪����")){
                                log.error("form.hhu.edu.cnϵͳ�쳣");
                                return Code.RETRY;
                            }
                            String wid = regEx("(?<=_selfFormWid = \\')(.*?)(?=\\')", page).get(0);
                            String uid = regEx("(?<=_userId = \\')(.*?)(?=\\')", page).get(0);
                            String fillDetail = regEx("(?<=fillDetail = )(.*?)(?=\\;)", page).get(0);
                            String json = "{\"XGH_566872\": \"ѧ��\",\"XM_140773\": \"����\",\"SFZJH_402404\": \"���֤��\",\"SZDW_439708\": \"ѧԺ\",\"ZY_878153\": \"רҵ\",\"GDXW_926421\": \"����ѧλ\",\"DSNAME_606453\":\"��ʦ\",\"PYLB_253720\": \"�������\",\"SELECT_172548\": \"����¥\",\"TEXT_91454\": \"�����\",\"TEXT_24613\": \"�ֻ���\",\"TEXT_826040\": \"������ϵ�˵绰\",\"RADIO_799044\": \"�������������\",\"RADIO_384811\": \"�������Ƿ���У��\",\"RADIO_907280\": \"���˽��������\",\"RADIO_716001\": \"ͬס�˽��������\",\"RADIO_248990\": \"���˼�ͬס��14�����Ƿ����и߷��յ����þ�ʷ��Ӵ����и߷��յ�����Ա��\"}";
                            JSONObject col = JSON.parseObject(json);
                            JSONArray fills = JSON.parseArray(fillDetail);
                            JSONObject fill = (JSONObject) fills.get(0);
                            SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd");
                            Date date = new Date(System.currentTimeMillis());

                            Iterator iter = col.entrySet().iterator();
                            List<NameValuePair> post = new ArrayList<>();
                            post.add(new BasicNameValuePair("DATETIME_CYCLE", formatter.format(date)));
                            while (iter.hasNext()) {
                                Map.Entry entry = (Map.Entry) iter.next();
                                post.add(new BasicNameValuePair(entry.getKey().toString(), fill.getString(entry.getKey().toString())));
                            }
                            System.out.println(post);
                            httpPost = new HttpPost("http://form.hhu.edu.cn/pdc/formDesignApi/dataFormSave?wid="+wid+"&userId="+uid);
                            httpPost.setEntity(new UrlEncodedFormEntity(post, StandardCharsets.UTF_8));
                            res = httpClient.execute(httpPost,context);
                            try {
                                if(EntityUtils.toString(res.getEntity()).equals("{\"result\":true}")){
                                    log.info("�򿨳ɹ�");
                                    iter = col.entrySet().iterator();
                                    while (iter.hasNext()) {
                                        Map.Entry entry = (Map.Entry) iter.next();
                                        log.info(entry.getValue()+":"+fill.getString(entry.getKey().toString()));
                                    }
                                    return Code.OK;
                                }
                                else{
                                    log.error("��ʧ��");
                                    return Code.RETRY;
                                }
                            } catch (ParseException e) {
                                log.error(e.toString());
                                return Code.RETRY;
                            }
                        }
                        else{
                            res.close();
                            log.error("form.hhu.edu.cnʶ��ʧ�ܣ���ݣ�δ֪");
                            return Code.RETRY;
                        }
                    }
                    else{
                        res.close();
                        log.error("��ҳ�����ʧ�ܣ�");
                        return Code.RETRY;
                    }
                }
                else{
                    log.error("Զ�̷������쳣");
                    return Code.RETRY;
                }
            }

        } catch (IOException e) {
            log.error(e.toString());
            return Code.EXIT;
        }
    }
    private static Code newReport(String username, String password) {
        if(password.matches("[0-9]+")){
            log.warn("����Ϊ�����룬�л���E�Ӻ��ӿ�");
            return Code.CHANGE_EHHU;
        }
        else{

            // ȫ����������
            RequestConfig globalConfig = RequestConfig.custom().setCookieSpec(StandardCookieSpec.STRICT).setCircularRedirectsAllowed(true).build();
            // ����cookie store�ı���ʵ��
            CookieStore cookieStore =  new BasicCookieStore();
            // ����HttpClient������
            HttpClientContext context = HttpClientContext.create();
            context.setCookieStore(cookieStore);

            // ����һ��HttpClient
            CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(globalConfig)
                    .setDefaultCookieStore(cookieStore).build();

            CloseableHttpResponse res =  null ;

            HttpGet httpGet = new HttpGet("http://authserver.hhu.edu.cn/authserver/needCaptcha.html?username="+username+"&pwdEncrypt2=pwdEncryptSalt&_=1630893279471");
            try {
                res = httpClient.execute(httpGet,context);
                try {
                    if(EntityUtils.toString(res.getEntity()).equals("true")){
                        res.close();
                        log.warn("���û���������֤�뷽�ɵ�¼�°��Ż����л���E�Ӻ��򿨽ӿ�");
                        return Code.CHANGE_EHHU;
                    }
                    else{
                        res.close();
                        log.info("����ʹ���°��Ż���");
                        httpGet = new HttpGet("http://authserver.hhu.edu.cn/authserver/login");
                        res = httpClient.execute(httpGet,context);
                        Document document = Jsoup.parse(EntityUtils.toString(res.getEntity()));
                        res.close();
                        String lt = Objects.requireNonNull(Objects.requireNonNull(document.getElementById("casLoginForm")).getElementsByAttributeValue("name", "lt").first()).attr("value");
                        String execution = Objects.requireNonNull(Objects.requireNonNull(document.getElementById("casLoginForm")).getElementsByAttributeValue("name", "execution").first()).attr("value");
                        String _eventId = Objects.requireNonNull(Objects.requireNonNull(document.getElementById("casLoginForm")).getElementsByAttributeValue("name", "_eventId").first()).attr("value");
                        String dllt = Objects.requireNonNull(Objects.requireNonNull(document.getElementById("casLoginForm")).getElementsByAttributeValue("name", "dllt").first()).attr("value");
                        String rmShown = Objects.requireNonNull(Objects.requireNonNull(document.getElementById("casLoginForm")).getElementsByAttributeValue("name", "rmShown").first()).attr("value");
                        String pwdDefaultEncryptSalt = Objects.requireNonNull(Objects.requireNonNull(document.getElementById("casLoginForm")).getElementById("pwdDefaultEncryptSalt")).attr("value");
                        String encrypt = encrypt(password,pwdDefaultEncryptSalt);
                        if(encrypt == null){
                            log.error("�������ʧ�ܣ��л���E�Ӻ��򿨽ӿ�");
                            return Code.CHANGE_EHHU;
                        }
                        else{
                            HttpPost httpPost = new HttpPost("http://authserver.hhu.edu.cn/authserver/login");
                            List<NameValuePair> nvps = new ArrayList<>();
                            nvps.add(new BasicNameValuePair("username", username));
                            nvps.add(new BasicNameValuePair("password", encrypt));
                            nvps.add(new BasicNameValuePair("lt", lt));
                            nvps.add(new BasicNameValuePair("dllt", dllt));
                            nvps.add(new BasicNameValuePair("execution", execution));
                            nvps.add(new BasicNameValuePair("_eventId", _eventId));
                            nvps.add(new BasicNameValuePair("rmShown", rmShown));
                            httpPost.setEntity(new UrlEncodedFormEntity(nvps));
                            res = httpClient.execute(httpPost,context);
                            String url ;
                            if(context.getRedirectLocations().size() >= 1){
                                url = context.getRedirectLocations().get(0).toString();
                            }
                            else{
                                url = "http://authserver.hhu.edu.cn/authserver/login";
                            }
                            if(url.contains("http://authserver.hhu.edu.cn/authserver/index.do")){
                                res.close();
                                log.info("�°��Ż���½�ɹ���");
                                httpGet = new HttpGet("http://dailyreport.hhu.edu.cn/pdc/form/list");
                                res = httpClient.execute(httpGet,context);
                                String page = EntityUtils.toString(res.getEntity());
                                if(page.contains("������")){
                                    if(page.contains("������")){
                                        res.close();
                                        log.info("dailyreport.hhu.edu.cnʶ��ɹ�����ݣ�������");
                                        httpGet = new HttpGet("http://dailyreport.hhu.edu.cn/pdc/formDesignApi/S/gUTwwojq");
                                        res = httpClient.execute(httpGet,context);
                                        page = EntityUtils.toString(res.getEntity());
                                        if(page.contains("δ֪����")){
                                            log.error("dailyreport.hhu.edu.cnϵͳ�쳣�������л�");
                                            return Code.CHANGE_EHHU;
                                        }
                                        String wid = regEx("(?<=_selfFormWid = \\')(.*?)(?=\\')", page).get(0);
                                        String uid = regEx("(?<=_userId = \\')(.*?)(?=\\')", page).get(0);
                                        String fillDetail = regEx("(?<=fillDetail = )(.*?)(?=\\;)", page).get(0);
                                        String json = "{\"XGH_336526\": \"ѧ��\",\"XM_1474\": \"����\",\"SFZJH_859173\": \"���֤��\",\"SELECT_941320\": \"ѧԺ\",\"SELECT_459666\": \"�꼶\",\"SELECT_814855\": \"רҵ\",\"SELECT_525884\": \"�༶\",\"SELECT_125597\": \"����¥\",\"TEXT_950231\": \"�����\",\"TEXT_937296\": \"�ֻ���\",\"RADIO_6555\": \"�������������\",\"RADIO_535015\": \"�������Ƿ���У��\",\"RADIO_891359\": \"���˽��������\",\"RADIO_372002\": \"ͬס�˽��������\",\"RADIO_618691\": \"���˼�ͬס��14�����Ƿ����и߷��յ����þ�ʷ��Ӵ����и߷��յ�����Ա��\"}";
                                        JSONObject col = JSON.parseObject(json);
                                        JSONArray fills = JSON.parseArray(fillDetail);
                                        JSONObject fill = (JSONObject) fills.get(0);
                                        SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd");
                                        Date date = new Date(System.currentTimeMillis());

                                        Iterator iter = col.entrySet().iterator();
                                        List<NameValuePair> post = new ArrayList<>();
                                        post.add(new BasicNameValuePair("DATETIME_CYCLE", formatter.format(date)));
                                        while (iter.hasNext()) {
                                            Map.Entry entry = (Map.Entry) iter.next();
                                            post.add(new BasicNameValuePair(entry.getKey().toString(), fill.getString(entry.getKey().toString())));
                                        }
                                        httpPost = new HttpPost("http://dailyreport.hhu.edu.cn/pdc/formDesignApi/dataFormSave?wid="+wid+"&userId="+uid);
                                        httpPost.setEntity(new UrlEncodedFormEntity(post, StandardCharsets.UTF_8));
                                        res = httpClient.execute(httpPost,context);
                                        if(EntityUtils.toString(res.getEntity()).equals("{\"result\":true}")){
                                            log.info("�򿨳ɹ�");
                                            iter = col.entrySet().iterator();
                                            while (iter.hasNext()) {
                                                Map.Entry entry = (Map.Entry) iter.next();
                                                log.info(entry.getValue()+":"+fill.getString(entry.getKey().toString()));
                                            }
                                            return Code.OK;
                                        }
                                        else{
                                            log.error("��ʧ��");
                                            return Code.RETRY;
                                        }
                                    }
                                    else if(page.contains("�о���")){
                                        res.close();
                                        log.info("dailyreport.hhu.edu.cnʶ��ɹ�����ݣ��о���");
                                        httpGet = new HttpGet("http://dailyreport.hhu.edu.cn/pdc/formDesignApi/S/xznuPIjG");
                                        res = httpClient.execute(httpGet,context);
                                        page = EntityUtils.toString(res.getEntity());
                                        if(page.contains("δ֪����")){
                                            log.error("dailyreport.hhu.edu.cnϵͳ�쳣�������л�");
                                            return Code.CHANGE_EHHU;
                                        }
                                        String wid = regEx("(?<=_selfFormWid = \\')(.*?)(?=\\')", page).get(0);
                                        String uid = regEx("(?<=_userId = \\')(.*?)(?=\\')", page).get(0);
                                        String fillDetail = regEx("(?<=fillDetail = )(.*?)(?=\\;)", page).get(0);
                                        String json = "{\"XGH_566872\": \"ѧ��\",\"XM_140773\": \"����\",\"SFZJH_402404\": \"���֤��\",\"SZDW_439708\": \"ѧԺ\",\"ZY_878153\": \"רҵ\",\"GDXW_926421\": \"����ѧλ\",\"DSNAME_606453\":\"��ʦ\",\"PYLB_253720\": \"�������\",\"SELECT_172548\": \"����¥\",\"TEXT_91454\": \"�����\",\"TEXT_24613\": \"�ֻ���\",\"TEXT_826040\": \"������ϵ�˵绰\",\"RADIO_799044\": \"�������������\",\"RADIO_384811\": \"�������Ƿ���У��\",\"RADIO_907280\": \"���˽��������\",\"RADIO_716001\": \"ͬס�˽��������\",\"RADIO_248990\": \"���˼�ͬס��14�����Ƿ����и߷��յ����þ�ʷ��Ӵ����и߷��յ�����Ա��\"}";
                                        JSONObject col = JSON.parseObject(json);
                                        JSONArray fills = JSON.parseArray(fillDetail);
                                        JSONObject fill = (JSONObject) fills.get(0);
                                        SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd");
                                        Date date = new Date(System.currentTimeMillis());

                                        Iterator iter = col.entrySet().iterator();
                                        List<NameValuePair> post = new ArrayList<>();
                                        post.add(new BasicNameValuePair("DATETIME_CYCLE", formatter.format(date)));
                                        while (iter.hasNext()) {
                                            Map.Entry entry = (Map.Entry) iter.next();
                                            post.add(new BasicNameValuePair(entry.getKey().toString(), fill.getString(entry.getKey().toString())));
                                        }
                                        System.out.println(post);
                                        httpPost = new HttpPost("http://dailyreport.hhu.edu.cn/pdc/formDesignApi/dataFormSave?wid="+wid+"&userId="+uid);
                                        httpPost.setEntity(new UrlEncodedFormEntity(post, StandardCharsets.UTF_8));
                                        res = httpClient.execute(httpPost,context);
                                        if(EntityUtils.toString(res.getEntity()).equals("{\"result\":true}")){
                                            log.info("�򿨳ɹ�");
                                            iter = col.entrySet().iterator();
                                            while (iter.hasNext()) {
                                                Map.Entry entry = (Map.Entry) iter.next();
                                                log.info(entry.getValue()+":"+fill.getString(entry.getKey().toString()));
                                            }
                                            return Code.OK;
                                        }
                                        else{
                                            log.error("��ʧ��");
                                            return Code.CHANGE_EHHU;
                                        }
                                    }
                                    else{
                                        res.close();
                                        log.error("dailyreport.hhu.edu.cnʶ��ʧ�ܣ���ݣ�δ֪");
                                        return Code.CHANGE_EHHU;
                                    }
                                }
                                else{
                                    res.close();
                                    log.error("��ҳ�����ʧ�ܣ�");
                                    return Code.CHANGE_EHHU;
                                }
                            }
                            else{
                                String page = EntityUtils.toString(res.getEntity());
                                res.close();
                                document = Jsoup.parse(page);
                                String msg = document.getElementById("msg").text();
                                if(msg.isEmpty()){
                                    log.error("�°��Ż���½ʧ�ܣ�");
                                }
                                else{
                                    log.error("�°��Ż���½ʧ�ܣ�Զ�̷�������ʾ:"+msg);
                                }
                                return Code.EXIT;
                            }
                        }
                    }
                } catch (ParseException e) {
                    log.error(e.toString());
                    return Code.CHANGE_EHHU;
                }
            } catch (IOException e) {
                log.error(e.toString());
                return Code.CHANGE_EHHU;
            }
        }
    }
}
enum Code {
    EXIT(0),
    CHANGE_EHHU(1),
    OK(2),
    RETRY(3),
    ;

    public int value;

    Code(int value) {
        this.value = value;
    }

}