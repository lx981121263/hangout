package com.ctrip.ops.sysdev.outputs;

import com.alibaba.fastjson.JSON;
import com.cloudbees.syslog.Facility;
import com.cloudbees.syslog.MessageFormat;
import com.cloudbees.syslog.Severity;
import com.cloudbees.syslog.sender.AbstractSyslogMessageSender;
import com.cloudbees.syslog.sender.SyslogMessageSender;
import com.cloudbees.syslog.sender.TcpSyslogMessageSender;
import com.cloudbees.syslog.sender.UdpSyslogMessageSender;
import com.ctrip.ops.sysdev.baseplugin.BaseOutput;
import com.ctrip.ops.sysdev.render.TemplateRender;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Log4j2
public class Syslog extends BaseOutput {

    private String protocol;
    private String app_name;
    private int send_port;
    private List<String> send_host;

    private TemplateRender format;

    private List<SyslogMessageSender> syslogMessageSenderList;

    public Syslog(Map config) {
        super(config);
    }

    @Override
    protected void prepare() {
        syslogMessageSenderList = new ArrayList<>();

        if (this.config.containsKey("format")) {
            String format = (String) this.config.get("format");
            try {
                this.format = TemplateRender.getRender(format);
            } catch (IOException e) {
                log.fatal("could not build template from" + format);
                System.exit(1);
            }
        } else {
            this.format = null;
        }

        try {
            this.protocol = getConfig(config, "protocol", "udp", false);
            this.app_name = getConfig(config, "app_name", "esp", false);
            this.send_port = getConfig(config, "send_port", 514, false);
            this.send_host = getConfig(config, "send_host", new ArrayList<>(), false);
        } catch (Exception e) {
            log.error("read syslog file failure", e);
            System.exit(1);
        }
        if (protocol != null) {
            if (send_host != null && send_host.size() > 0) {
                for (String host : send_host) {
                    AbstractSyslogMessageSender syslogMessageSender = getSyslogMessageSender(protocol);
//                    syslogMessageSender.setDefaultMessageHostname("localhost");
                    syslogMessageSender.setDefaultAppName(app_name);
                    syslogMessageSender.setDefaultFacility(Facility.SYSLOG);
                    syslogMessageSender.setDefaultSeverity(Severity.INFORMATIONAL);
                    syslogMessageSender.setSyslogServerHostname(host);
                    syslogMessageSender.setSyslogServerPort(send_port);
                    syslogMessageSender.setMessageFormat(MessageFormat.RFC_3164);
                    syslogMessageSenderList.add(syslogMessageSender);
                }
            }
        }
    }

    @Override
    protected void emit(Map event) {
        if (!syslogMessageSenderList.isEmpty()) {
            Object message = this.format != null ? this.format.render(event) : event;
            if (message != null) {
                String msg = JSON.toJSONString(message);
                for (SyslogMessageSender syslogMessageSender : syslogMessageSenderList) {
                    try {
                        syslogMessageSender.sendMessage(msg);
                        log.info("send syslog msg:{}", msg);
                    } catch (IOException e) {
                        log.error("send syslog msg failure", e);
                    }
                }
            }
        }
    }

    private AbstractSyslogMessageSender getSyslogMessageSender(String protocol) {
        if ("tcp".equals(protocol)) {
            return new TcpSyslogMessageSender();
        } else {
            return new UdpSyslogMessageSender();
        }
    }

}
