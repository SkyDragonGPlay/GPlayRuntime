# 资源下载和截图协议接口

## 概述

资源下载和截图的功能都是通过调用GPlay SDK的扩展接口来完成。

## 场景资源下载

场景资源下载过程中会涉及到 "下载进度"、"下载重试"、"通知完成"等三个业务类型，下面分别用对应的"设置下载回调接口"、"下载重试接口"、"通知完成接口" 来说这几个接口的使用方法。

### 接口名称

#### 设置下载回调接口

主动下载场景的时候，GPlay 会调用"下载回调接口", 传入下载进度等信息。 

`IGPlayService.invokeMethodAysnc(String method, Map<String,Object> args, ICallback callback)`

##### 参数说明

- method  

值为 `PRELOAD_SCENES_SET_CALLBACK`

- args

直接传入 null

- callback

    - 回调接口
    - 第一个参数 from 对应的值是`PRELOAD_SCENES_SET_CALLBACK`
    - 第二个参数 args 对应的是下载传入的数据
  
###### args包含的数据说明
| Map 键值 | 说明 |
| :----  | :-- |
| result_code | Integer, 结果码, `-1 调用方法失败` `1 下载成功` `2 下载中` `3 下载失败`|
| error_code | Integer, 错误码(只有下载失败时才有这个键值对): `-1 未知错误` `1 网络错误` `2 SD卡没有空间` `3 文件校验失败`|
| result_msg |  返回调用方法结果信息 |
| scene_name | String, 场景名称 |
| percent | Float, 下载进度 |
| stage | Integer, 1 表示下载中, 2 表示解压缩中 |
| download_speed | Float, 下载速度 |
    
#### 下载重试接口

当场景资源下载失败的时候，允许玩家进行 "重试" 或 "退出游戏" 的操作，当调用重试接口后，GPlay会再一次下载场景分组资源。

`Object IGPlayService.invokeMethodSync(String method, Map<String,Object> args);`

##### 参数说明

- method  

值为 `PRELOAD_SCENES_RETRY`

- args

直接传入 null

#### 通知完成接口

当场景资源下载完成之后, 渠道方需要关闭下载进度条等提示界面, 并将此消息通知给GPlay, GPlay 接收到此消息后会通知游戏场景资源下载完成。

`Object IGPlayService.invokeMethodSync(String method, Map<String,Object> args);`

##### 参数说明

- method  

值为 `PRELOAD_SCENES_ONFINISH `

- args

直接传入 null

## 游戏截图

当调用此接口后，会将游戏当前帧的内容保存到调用方指定的文件中，保存的图片尺寸对应的是当前手机的分辨率，调用方需要根据自己的业务情况对此图片做一定的处理。

`IGPlayService.invokeMethodAysnc(String method, Map<String,Object> args, ICallback callback)`

### 参数说明

- method

对应的值为`CAPTURE_SCREEN`

- args

必传参数`FILE_NAME`：指定游戏截图存放的JPEG文件全路径  
可选参数`QUALITY`：指定JPEG保存的压缩品质，数值范围[0-100]  

- callback
    - 回调接口
    - 第一个参数 from 对应的值是`CAPTURE_SCREEN`
    - 第二个参数 args 对应的是截图的结果数据

###### 返回的args数据说明
| Map 键值 | 类型 | 说明 |
| :----  | :-- | :-- |
| result_code | Integer | `-1 调用方法失败` `1 成功`|  
| result_msg | String | 截图结果描述信息 |
| file_name | String | 保存的文件名（全路径） |