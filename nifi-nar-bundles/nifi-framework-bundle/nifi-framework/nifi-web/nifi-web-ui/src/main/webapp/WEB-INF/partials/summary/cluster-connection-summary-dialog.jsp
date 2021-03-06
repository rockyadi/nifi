<%--
 Licensed to the Apache Software Foundation (ASF) under one or more
  contributor license agreements.  See the NOTICE file distributed with
  this work for additional information regarding copyright ownership.
  The ASF licenses this file to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance with
  the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
--%>
<%@ page contentType="text/html" pageEncoding="UTF-8" session="false" %>
<div id="cluster-connection-summary-dialog">
    <div class="dialog-content">
        <div id="cluster-connection-summary-header">
            <div id="cluster-connection-refresh-button" class="summary-refresh pointer" title="Refresh"></div>
            <div id="cluster-connection-summary-last-refreshed-container">
                Last updated:&nbsp;<span id="cluster-connection-summary-last-refreshed"></span>
            </div>
            <div id="cluster-connection-summary-loading-container" class="loading-container"></div>
            <div id="cluster-connection-details-container">
                <div id="cluster-connection-icon"></div>
                <div id="cluster-connection-details">
                    <div id="cluster-connection-name"></div>
                    <div id="cluster-connection-id"></div>
                    <div id="cluster-connection-group-id" class="hidden"></div>
                </div>
            </div>
        </div>
        <div id="cluster-connection-summary-table"></div>
    </div>
</div>
