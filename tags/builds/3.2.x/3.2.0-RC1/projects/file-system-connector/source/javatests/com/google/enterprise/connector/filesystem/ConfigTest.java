// Copyright 2009 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.enterprise.connector.filesystem;

import com.google.enterprise.connector.spi.Connector;
import com.google.enterprise.connector.util.diffing.testing.TestDirectoryManager;

import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.ClassPathResource;

import junit.framework.TestCase;

import java.io.File;
import java.util.HashMap;
import java.util.Properties;

/**
 */
public class ConfigTest extends TestCase {
  private static final String CONFIG_DIR = "config/";
  private static final String DEFAULTS_CONFIG_FILE = CONFIG_DIR
      + "connectorDefaults.xml";
  private static final String INSTANCE_CONFIG_FILE = CONFIG_DIR
      + "connectorInstance.xml";
  private HashMap<String, String> goodConfig;

  @Override
  public void setUp() throws Exception {

    File startDir = new TestDirectoryManager(this).makeDirectory("startdir");

    goodConfig = new HashMap<String, String>();

    int numInputs = FileConnectorType.getMaxInputsOfMultiLineFieldForTesting();
    for (int i = 0; i < numInputs; i++) {
      goodConfig.put("start_" + i, "");
      goodConfig.put("include_" + i, "");
      goodConfig.put("exclude_" + i, "");
    }

    goodConfig.put("start_0", startDir.getAbsolutePath());
    goodConfig.put("domain", "domain1");
    goodConfig.put("user", "xyz");
    goodConfig.put("password", "test");
    goodConfig.put("fulltraversal", "0");
    goodConfig.put("googleGlobalNamespace", "GlobalNS");
    goodConfig.put("googleLocalNamespace", "LocalNS");
  }

  public void testInstantiation() {
    Properties props = new Properties();
    props.putAll(goodConfig);
    DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
    XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(beanFactory);
    // Refer to InstanceInfo.makeConnectorWithSpring
    //(com.google.enterprise.connector.instantiator) for more info on how
    // these files are loaded to instantiate connector.
    reader.loadBeanDefinitions(new ClassPathResource(
        DEFAULTS_CONFIG_FILE, ConfigTest.class));
    reader.loadBeanDefinitions(new ClassPathResource(
        INSTANCE_CONFIG_FILE, ConfigTest.class));
    PropertyPlaceholderConfigurer cfg = new PropertyPlaceholderConfigurer();
    cfg.setProperties(props);
    cfg.postProcessBeanFactory(beanFactory);
    String[] beans = beanFactory.getBeanNamesForType(Connector.class);
    assertEquals(1, beans.length);
    Object obj = beanFactory.getBean(beans[0]);
    assertTrue("Expecting instance of Connector interface but the actual "
        + "instance: " + obj.getClass().toString(), obj instanceof Connector);
  }
}
