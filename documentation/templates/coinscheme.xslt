<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                xmlns:rdfs="http://www.w3.org/2000/01/rdf-schema#"
                xmlns:owl="http://www.w3.org/2002/07/owl#"
                xmlns:xsd="http://www.w3.org/2001/XMLSchema#"
                xmlns:coin="http://purl.org/court/def/2009/coin#"
                xmlns="http://www.w3.org/1999/xhtml">

    <xsl:key name="rel" match="/graph/resource" use="@uri"/>
    <xsl:variable name="r" select="/graph/resource"/>

    <xsl:template match="/graph">
        <html>
            <head>
                <title></title>
                <link rel="stylesheet" type="text/css" href="" />
            </head>
            <body>
                <xsl:apply-templates select="resource[a/coin:CoinScheme]"/>
            </body>
        </html>
    </xsl:template>

    <xsl:template match="resource[a/coin:CoinScheme]">
        <xsl:variable name="sep" select="coin:separator"/>
        <div class="coinscheme">
            <h1>URI-myntningsschema</h1>
            <p>
                <xsl:text>Bas-URI: </xsl:text>
                <span id="baseuri">
                    <xsl:apply-templates select="coin:root"/>
                </span>
            </p>
            <div id="bases">
                <!-- <h2>Regler förankrade i bas-segment</h2> -->
                <dl>
                    <xsl:for-each select="$r[a/coin:Base and
                                coin:scheme/@ref = current()/@uri]">
                        <xsl:sort select="coin:segment"/>
                        <xsl:apply-templates select="."/>
                    </xsl:for-each>
                </dl>
            </div>
            <div id="containments">
                <!-- <h2>Baserade på URI för relaterad resurs</h2> -->
                <dl>
                    <xsl:for-each select="$r[a/coin:Containment and
                                coin:scheme/@ref = current()/@uri]">
                        <xsl:sort select="coin:segment | coin:fragmentPrefix"/>
                        <xsl:apply-templates select="."/>
                    </xsl:for-each>
                </dl>
            </div>
        </div>
    </xsl:template>

    <xsl:template match="*[coin:contains | coin:component | coin:segment]">
        <dt>
            <xsl:apply-templates select="coin:segment"/>
            <xsl:if test="coin:segment and coin:component">/</xsl:if>
            <xsl:apply-templates select="coin:component"/>
            <xsl:if test="coin:contains">
                <xsl:text>/</xsl:text>
            </xsl:if>
        </dt>
        <dd>
            <xsl:if test="coin:forType">
                <em>(Av typen: <xsl:apply-templates select="coin:forType/@ref"/>)</em>
            </xsl:if>
            <xsl:if test="coin:contains">
                <dl>
                    <xsl:for-each select="coin:contains">
                        <xsl:sort select="coin:component[not(../coin:segment)]/@ref |
                                coin:segment"/>
                        <xsl:sort select="coin:component/@ref"/>
                        <xsl:apply-templates select="."/>
                    </xsl:for-each>
                </dl>
            </xsl:if>
        </dd>
    </xsl:template>

    <xsl:template match="*[a/coin:Containment]">
        <p><strong>Inkapsling</strong></p>
        <xsl:choose>
            <xsl:when test="coin:segment">
                <xsl:variable name="baseRelRange"
                              select="key('rel', coin:baseRel/@ref)/rdfs:range"/>
                <p>
                    <em>En
                        <xsl:apply-templates select="key('rel', coin:baseRel/@ref)/rdfs:domain/@ref"/>
                    som underordnas en
                    <xsl:apply-templates select="$baseRelRange/@ref"/></em>
                </p>
                <em>{
                    <xsl:apply-templates select="$baseRelRange/@ref"/>
                    &#8592;
                    <xsl:apply-templates select="coin:baseRel/@ref"/>
                    &#8596;
                    <xsl:apply-templates
                        select="key('rel', coin:baseRel/@ref)/owl:inverseOf/rdfs:label |
                                key('rel', coin:baseRel/@ref)/owl:inverseOf/@ref"/>
                    &#8594;
                }</em>
                <xsl:text>/</xsl:text>
                <xsl:apply-templates select="coin:segment"/>
                <xsl:text>/</xsl:text>
                <em>{ <xsl:apply-templates select="coin:component/@ref"/> }</em>
            </xsl:when>
            <xsl:when test="coin:fragmentPrefix">
                <em>{ Resurs &#8592;
                    <xsl:apply-templates
                        select="key('rel', coin:baseRev/@ref)/owl:inverseOf/rdfs:label |
                                key('rel', coin:baseRev/@ref)/owl:inverseOf/@ref"/>
                    &#8596;
                    <xsl:apply-templates select="coin:baseRev/@ref"/>
                    &#8594;
                } </em>
                <xsl:apply-templates select="coin:fragmentPrefix"/>
                <em>{ <xsl:apply-templates select="coin:component/@ref"/> }</em>
            </xsl:when>
        </xsl:choose>
    </xsl:template>

    <xsl:template match="coin:component">
        <em>{ <xsl:apply-templates select="@ref"/> </em>
        <xsl:variable name="tokenSet" select="key('rel', ../coin:useTokenSet/@ref)"/>
        <xsl:if test="$tokenSet">
            <xsl:text>: </xsl:text>
            <select name="tokenSet">
                <xsl:for-each select="$r[coin:tokenSet/@ref = $tokenSet/@uri]">
                    <xsl:sort select="rdf:value"/>
                    <option><xsl:apply-templates select="rdf:value"/></option>
                </xsl:for-each>
            </select>
        </xsl:if>
        <em> }</em>
    </xsl:template>

    <xsl:template match="@ref">
        <xsl:text> </xsl:text>
        <xsl:variable name="label" select="key('rel', .)/rdfs:label"/>
        <a href="{.}">
            <xsl:choose>
                <xsl:when test="$label">
                    <xsl:apply-templates select="$label"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:apply-templates/>
                </xsl:otherwise>
            </xsl:choose>
        </a>
    </xsl:template>

    <xsl:template match="coin:root | coin:segment">
        <strong><code><xsl:apply-templates/></code></strong>
    </xsl:template>

    <xsl:template match="coin:fragmentPrefix">
        <strong>
            <code>#<xsl:apply-templates/></code>
        </strong>
    </xsl:template>

</xsl:stylesheet>