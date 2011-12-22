<!DOCTYPE xsl:stylesheet SYSTEM "collector_log.dtd">
<xsl:stylesheet version="1.0"
                xmlns="http://www.w3.org/1999/xhtml"
                extension-element-prefixes="date"
                xmlns:date="http://exslt.org/dates-and-times"
                exclude-result-prefixes="xsd dct iana rx awol tl rc">

  <xsl:param name="mediabase" select="'media'"/>

  <xsl:key name="rel" match="/graph/resource" use="@uri"/>
  <xsl:variable name="r" select="/graph/resource"/>

  <xsl:output method="html" indent="yes" encoding="utf-8"/>


  <xsl:template match="*[a/rc:Collect]">
    <div class="collect">
      <h2>
        <xsl:choose>
        <xsl:when test="tl:end">Utförd</xsl:when>
        <xsl:otherwise>Ofullständig</xsl:otherwise>
        </xsl:choose>
        <xsl:text> insamling</xsl:text>
      </h2>
      <dl class="summary">
        <dt>Start:</dt>
        <dd><xsl:apply-templates select="tl:start"/></dd>
        <xsl:for-each select="tl:end">
          <dt>Stopp:</dt>
          <dd><xsl:apply-templates select="."/></dd>
        </xsl:for-each>
        <!-- TODO: Xalan doesn't support date:difference !
        <dt>Tid:</dt>
        <dd>
          <xsl:variable name="dur" select="date:difference(tl:start, tl:end)"/>
          <xsl:variable name="hours"
                        select="substring-before(substring-after($dur, 'P'), 'T')"/>
          <xsl:if test="$hours">
            <xsl:value-of select="$hours"/>
            <xsl:text>timmar </xsl:text>
          </xsl:if>
          <xsl:value-of select="substring-before(substring-after($dur, 'T'), 'M')"/>
          <xsl:text> min </xsl:text>
          <xsl:variable name="fullsecs"
                        select="substring-before(substring-after($dur, 'M'), 'S')"/>
          <xsl:value-of select="substring-before($fullsecs, '.')"/>
          <xsl:text> s</xsl:text>
        </dd>
        -->
      </dl>
      <xsl:for-each select="iana:via">
        <xsl:apply-templates select="key('rel', ./@ref)"/>
      </xsl:for-each>
    </div>
  </xsl:template>

  <xsl:template match="*[a/rc:PageError]">
    <div class="pageerror">
      <h2 class="error">Sidfel</h2>
      <dl class="summary">
        <dt class="dateTime">Tidpunkt</dt>
        <dd><xsl:apply-templates select="tl:at"/></dd>
        <dt>Källa:</dt>
        <dd><xsl:apply-templates select="dct:source/@ref"/></dd>
        <dt>Meddelande</dt>
        <xsl:value-of select="rdf:value"/>
      </dl>
    </div>
  </xsl:template>

  <xsl:template match="*[a/awol:Feed]">
    <xsl:variable name="collected" select="//*[a/awol:Entry and awol:source/@ref = current()/@uri]"/>
    <xsl:variable name="collect-count" select="count($collected)"/>
    <div class="source">
      <h3>Feed-källa</h3>
      <dl class="summary">
        <dt>Identifierare:</dt>
        <dd><xsl:apply-templates select="awol:id"/></dd>
        <dt>Feed-sidans URI:</dt>
        <dd><xsl:apply-templates select="iana:self/@ref"/></dd>
        <dt>Uppdaterad:</dt>
        <dd><xsl:apply-templates select="awol:updated"/></dd>
        <dt>Antal poster:</dt>
        <dd><xsl:value-of select="$collect-count"/></dd>
        <xsl:variable name="sucess-count"
                      select="count($collected/parent::resource[a/awol:Entry])"/>
        <xsl:if test="$collect-count != $sucess-count">
          <dt class="error">Antal fel:</dt>
          <dd class="error">
            <xsl:value-of select="$collect-count - $sucess-count"/>
          </dd>
        </xsl:if>
      </dl>
      <h4>Poster</h4>
      <table class="report">
        <tr>
          <th class="position">#</th>
          <th class="dateTime">Tidpunkt</th>
          <th class="status">Status</th>
          <th class="uri">ID</th>
          <th class="info">Information</th>
        </tr>
        <xsl:for-each select="$collected">
          <xsl:sort select="awol:updated | tl:at" order="descending"/>
          <xsl:apply-templates select="parent::resource" mode="trow">
            <xsl:with-param name="pos" select="$collect-count + 1 - position()"/>
          </xsl:apply-templates>
        </xsl:for-each>
      </table>
    </div>
  </xsl:template>

  <xsl:template match="*[a/awol:Entry]" mode="trow">
    <xsl:param name="pos"/>
    <tr class="entry">
      <td class="position"><xsl:value-of select="$pos"/></td>
      <td><xsl:apply-templates select="awol:updated"/></td>
      <td class="status">OK</td>
      <td><xsl:apply-templates select="rx:primarySubject/@ref"/></td>
      <td></td>
    </tr>
  </xsl:template>

  <!-- TODO:
        ov:DeletedEntry
  -->

  <xsl:template match="*[a/rc:ChecksumError]" mode="trow">
    <xsl:param name="pos"/>
    <tr class="error">
      <td class="position"><xsl:value-of select="$pos"/></td>
      <td><xsl:apply-templates select="tl:at"/></td>
      <td class="status">Fel MD5-summa</td>
      <td><xsl:apply-templates select="iana:via/awol:id"/></td>
      <td>
        <dl class="lone">
          <dt>Dokument:</dt>
          <dd><xsl:value-of select="rc:document"/></dd>
          <dt>Angiven MD5:</dt>
          <dd><xsl:value-of select="rc:givenMd5"/></dd>
          <dt>Beräknad MD5:</dt>
          <dd><xsl:value-of select="rc:computedMd5"/></dd>
        </dl>
      </td>
    </tr>
  </xsl:template>

  <xsl:template match="*[a/rc:LengthError]" mode="trow">
    <xsl:param name="pos"/>
    <tr class="error">
      <td class="position"><xsl:value-of select="$pos"/></td>
      <td><xsl:apply-templates select="tl:at"/></td>
      <td class="status">Fel storlek</td>
      <td><xsl:apply-templates select="iana:via/awol:id"/></td>
      <td>
        <dl class="lone">
          <dt>Dokument:</dt>
          <dd><xsl:value-of select="rc:document"/></dd>
          <dt>Angiven filstorlek:</dt>
          <dd><xsl:value-of select="rc:givenLength"/></dd>
          <dt>Beräknad filstorlek:</dt>
          <dd><xsl:value-of select="rc:computedLength"/></dd>
        </dl>
      </td>
    </tr>
  </xsl:template>

  <xsl:template match="*[a/rc:IdentifyerError]" mode="trow">
    <xsl:param name="pos"/>
    <tr class="error">
      <td class="position"><xsl:value-of select="$pos"/></td>
      <td><xsl:apply-templates select="tl:at"/></td>
      <td class="status">URI-fel</td>
      <td><xsl:apply-templates select="iana:via/awol:id"/></td>
      <td>
        <dl class="lone">
          <dt>Angiven URI:</dt>
          <dd><xsl:value-of select="rc:givenUri"/></dd>
          <dt>Beräknad URI:</dt>
          <dd><xsl:value-of select="rc:computedUri"/></dd>
        </dl>
      </td>
    </tr>
  </xsl:template>

  <xsl:template match="*[a/rc:DescriptionError]" mode="trow">
    <xsl:param name="pos"/>
    <tr class="error">
      <td class="position"><xsl:value-of select="$pos"/></td>
      <td><xsl:apply-templates select="tl:at"/></td>
      <td class="status">Beskrivningsfel</td>
      <td><xsl:apply-templates select="iana:via/awol:id"/></td>
      <td>
        <ul>
          <xsl:for-each select="rc:reports">
            <li>
              <dl>
                <dt>
                  <xsl:choose>
                    <xsl:when test="a/sch:Error">Fel</xsl:when>
                    <xsl:when test="a/sch:Warning">Varning</xsl:when>
                    <xsl:otherwise>
                      <xsl:value-of select="local-name(a/*)"/>
                    </xsl:otherwise>
                  </xsl:choose>
                  <xsl:text>:</xsl:text>
                </dt>
                <dd>
                  <xsl:value-of select="sch:message"/>
                  <ol>
                    <xsl:for-each select="sch:implicated/li">
                      <li>
                        <xsl:apply-templates select="." mode="repr"/>
                      </li>
                    </xsl:for-each>
                  </ol>
                </dd>
                <!--<dt>Källfil:</dt>
                <dd><xsl:apply-templates select="dct:source/@ref"/></dd>-->
                <dt>Testdefintion:</dt>
                <dd>
                  <xsl:for-each select="rdfs:isDefinedBy/@ref">
                    <a href="{.}"><xsl:apply-templates select="."/></a>
                  </xsl:for-each>
                </dd>
              </dl>
            </li>
          </xsl:for-each>
        </ul>
      </td>
    </tr>
  </xsl:template>

  <xsl:template match="*[a/rc:Error]" mode="trow">
    <xsl:param name="pos"/>
    <tr class="error">
      <td class="position"><xsl:value-of select="$pos"/></td>
      <td><xsl:apply-templates select="tl:at"/></td>
      <td class="status">Fel</td>
      <td><xsl:apply-templates select="iana:via/awol:id"/></td>
      <td>
          <xsl:value-of select="rdf:value"/>
      </td>
    </tr>
  </xsl:template>

  <xsl:template match="@ref | xsd:anyURI">
    <code><xsl:value-of select="."/></code>
  </xsl:template>

  <xsl:template match="*" mode="repr">
    <xsl:choose>
      <xsl:when test="@ref">
        <xsl:apply-templates select="@ref"/>
      </xsl:when>
      <xsl:when test="@fmt = 'datatype'">
        <code><xsl:value-of select="*"/></code>
        <xsl:text>^^</xsl:text>
        <xsl:value-of select="name(*)"/>
      </xsl:when>
      <xsl:otherwise>
        <code><xsl:value-of select="."/></code>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <xsl:template match="xsd:dateTime">
    <xsl:value-of select="substring-before(., 'T')"/>
    <xsl:text> </xsl:text>
    <xsl:value-of select="substring-after(., 'T')"/>
  </xsl:template>

</xsl:stylesheet>
