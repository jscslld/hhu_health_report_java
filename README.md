# 河海大学自动打卡（GitHub Action）
## 停更提醒
由于学校更换打卡平台，该项目不再更新，新的打卡脚本请参考https://github.com/jscslld/hhu_health_report
## 使用方法
1. 首先先将本项目Fork到自己用户下
![image](https://user-images.githubusercontent.com/13760010/147191441-20e4ed7c-40f7-4925-b8f5-3dd580043f3f.png)
2. 在Settings > Secrets中添加一个名称为USER的secrets
![image](https://user-images.githubusercontent.com/13760010/147191533-42535482-2709-466c-91f4-5abe15b7b55d.png)
3. secrets的格式为JSON数组，举例：
``` plain
[{'username':'用户名1','password':'密码1'},{'username':'用户名2','password':'密码2'}]
```
4. 进入Actions > health_report中，点击Run workflow，测试是否能正常使用
![image](https://user-images.githubusercontent.com/13760010/147191867-b45b40b0-de86-480b-95bb-8cf6101b4e14.png)
5. 接下来的每天上午8点，系统会自动进行打卡任务。
## To-Do
1.  完善重试逻辑
2.  添加邮件提醒
