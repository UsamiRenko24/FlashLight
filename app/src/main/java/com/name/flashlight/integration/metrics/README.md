数据收集管理系统 V2.0
====
- 1、Ua信息采集
- 2、归因信息采集
- 3、付费信息采集
- 4、自定义事件打点
- 5、设备信息采集

任务目标:
----
    1: 数据结构protobuf处理
    2: 关键数据实时上报(如购买，订阅，买量关键行为)
    3: 非核心数据进行数据合并上报

数据合并上报:
----
    0: 第一次打开,一定上报一次
    1: 打开应用上报(当用户打开app,发现本地有任务未上报就上报,同时上报打开)
    2: 进入前后台上报(当用户进入前后台一定时间那么就上报,例如用户进入后台)
    3: 退出应用上报(退出应用马上上报)
    4: 常规上报(定时上报 例如每十秒判断上报队列是否有数据,有就上报)
    5: 每次上报时间间隔设定()
    6: 上报失败转至备用服务器发送日志
    7: 防止数据丢失(多个服务器上报, AB服务器, 合并上报)

    合并数据结构
        {
            {
                type:"ver", data:"1.0",
            }
            {
                type:"bs", data:"{
                    {ver:app版本, 
                    bid:app包名, 
                    device:设备型号, 
                    systemver:设备版本, 
                    guid:设备唯一标识, 
                    locale:地区, 
                    language:语言, 
                    zone:时区, 
                    timestamp:客户端时间,
                    chl:渠道值（默认就是"AppStore"）
                    }
                }",
            }
            {
                type:"ua", data:"{
                    ua:ua字符串
                }"
            }
            {
                type:"referrer", data:"{
                    ???
                }"
            }
            {
                type:"order", data:"{
                        trial:是否试用 ——》 1 或 0,
                        tid:订单ID,
                        otid:原始订单ID,
                        pid:产品ID,
                        price:价格
                }"
            }
            {
                type:"event", data:"{
                    Key:10000, Val:{}, Extend:String
                }"
            }
        }


协议整理新建:
----
    {
        192.168.1.5:8081/ua(UA)
        192.168.1.5:8081/referrer(归因)
        192.168.1.5:8081/order(订单)
        192.168.1.5:8081/event(打点)
        192.168.1.5:8081/merge(合并)
        <!-- 192.168.1.5:8081/device -->
    }



协议整理旧的:
----
    {
        report/ua (上报UA) ReportUserUa
        {
            <!-- 使用 -->
            <!-- ip, -->
            base,{app版本, app包名, 设备型号, 设备版本, 设备唯一标识, 地区, 语言, 时区, 客户端时间}
            <!-- guid,    -->
            <!-- bid,    客户端包名 -->
            <!-- ver,    客户端版本 -->
            <!-- upid, -->
            ua
        }
    }
    
    {
        report/ua/v2(上报UA) ReportUserUaWithEncrypt
        {

            ip
            guid,
            bid,
            upid,
            ua
        }        
    }

    {
        /key/action(上报设备) ReportKeyAction

        {
            base,{app版本, app包名, 设备型号, 设备版本, 设备唯一标识, 地区, 语言, 时区, 客户端时间}

            <!-- guid, -->
            <!-- bid,  -->
            act/action,
            num/number,
            placement_id, 
            ad_source_id, 
            network_placement_id, 
            cm, 
            ad
        }
    }

    {
        /report/order(上报订单) ReportPay
        {
            guid
            bid
            tid
            otid
            pid
            trial
        }
    }

    {
        /report/ap/data(用户归因[asa]) ReportApData
        {
            guid
            bid
            open
            req
            vs
            creativesetId, 
            keywordId, 
            adgroupId,
            body
        }
    }

    {
        /client/reportads(用户归因) ClientReportUa
        {
            adsUser.Guid, 
            adsUser.Bid, 
            "asa_user_data", 
            strconv.Itoa(adsUser.KeywordId),
		    strconv.Itoa(adsUser.AdGroupId), 
            strconv.Itoa(adsUser.CampaignId), 
            string(o.reqBody)
        }
    }

    {
        /report/purchaseuser(买量用户信息上报收集) ReportPurchaseUser
        {
            purchaseUser.Guid, 
            purchaseUser.Bid, 
            purchaseUser.AdId, 
            purchaseUser.AdGroupId, 
            purchaseUser.CampaignId,
            purchaseUser.Channel
        }
    }


Ua信息采集(UaController)
----
{

}


设备信息采集(DeviceController)
----
{

}

归因信息采集(ReferrerController)
----
{

}


付费订单信息采集(OrderController)
----
{

}


事件信息采集(EventController)
----
{

}

合并上报(MergeController)
----
{

}
