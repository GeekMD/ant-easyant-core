<!--
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
-->
<xsl:stylesheet version="1.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
<xsl:output method="text"/>
<xsl:template match="/ivy-report">
/* * directed graph dot input file. * * generated by ivy report */
            digraph G {
     "<xsl:value-of select="info/@organisation"/>-<xsl:value-of select="info/@module"/>" [label="<xsl:value-of select="info/@module"/>"];
     <xsl:for-each select="dependencies/module">
        "<xsl:value-of select="@organisation"/>-<xsl:value-of select="@name"/>" [label="<xsl:value-of select="@name"/>
            <xsl:for-each select="revision">
                  <xsl:text>\n</xsl:text>
                  <xsl:value-of select="@name"/><xsl:if test="@error"> (error)</xsl:if><xsl:if test="@evicted"> (evicted)</xsl:if>
            </xsl:for-each>
         <xsl:text>"];
</xsl:text>
      </xsl:for-each>
      <xsl:apply-templates select="dependencies/module/revision[not(@evicted)]/easyant/imports/import">
          <xsl:with-param name="parent_organisation" select="dependencies/module/@organisation"/>
          <xsl:with-param name="parent_name" select="dependencies/module/@name"/>
          <xsl:with-param name="parent_revision" select="dependencies/module/@revision"/>
      </xsl:apply-templates>
      <xsl:text>}</xsl:text>          
</xsl:template>

<xsl:template match="import">
<xsl:param name="parent_organisation"/>
<xsl:param name="parent_name"/>
<xsl:param name="parent_revision"/>
<xsl:text>"</xsl:text>
          <xsl:value-of select="@organisation"/>-<xsl:value-of select="@name"/>
          <xsl:text>" -> "</xsl:text>
          <xsl:value-of select="$parent_organisation"/>-<xsl:value-of select="$parent_name"/>
          <xsl:text>" [label="</xsl:text>
                  <xsl:value-of select="$parent_revision"/>
          <xsl:text>"]</xsl:text>
          <xsl:text>;
</xsl:text>

      <xsl:apply-templates select="easyant/imports/import">
          <xsl:with-param name="parent_organisation" select="@organisation"/>
          <xsl:with-param name="parent_name" select="@name"/>
          <xsl:with-param name="parent_revision" select="@revision"/>
      </xsl:apply-templates>
  
</xsl:template>

</xsl:stylesheet>
