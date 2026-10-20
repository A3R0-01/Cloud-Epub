$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression
$fixtureAssets = Join-Path $PSScriptRoot 'app/src/main/assets'
New-Item -ItemType Directory -Force -Path $fixtureAssets | Out-Null
$fixturePath = Join-Path $fixtureAssets 'fixture.epub'
$fixtureStream = [IO.File]::Open($fixturePath, [IO.FileMode]::Create)
$fixtureZip = [IO.Compression.ZipArchive]::new($fixtureStream, [IO.Compression.ZipArchiveMode]::Create)
function Add-FixtureEntry([string]$Name, [byte[]]$Bytes) {
    $entry = $fixtureZip.CreateEntry($Name, [IO.Compression.CompressionLevel]::NoCompression)
    $entry.LastWriteTime = [DateTimeOffset]::new(2026, 10, 16, 0, 0, 0, [TimeSpan]::Zero)
    $stream = $entry.Open()
    try { $stream.Write($Bytes, 0, $Bytes.Length) } finally { $stream.Dispose() }
}
function Add-FixtureText([string]$Name, [string]$Text) {
    Add-FixtureEntry $Name ([Text.Encoding]::UTF8.GetBytes($Text))
}
try {
    Add-FixtureText 'mimetype' 'application/epub+zip'
    Add-FixtureText 'META-INF/container.xml' '<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="EPUB/package.opf" media-type="application/oebps-package+xml"/></rootfiles></container>'
    Add-FixtureText 'EPUB/package.opf' @'
<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">urn:cloudepub:synthetic:2026-10-20:v2</dc:identifier><dc:title>Original-content anchor probes</dc:title><dc:language>en</dc:language><dc:creator>Cloud EPUB synthetic fixture</dc:creator><meta property="dcterms:modified">2026-10-20T00:00:00Z</meta></metadata><manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/><item id="one" href="one.xhtml" media-type="application/xhtml+xml"/><item id="two" href="two.xhtml" media-type="application/xhtml+xml" properties="svg"/><item id="three" href="three.xhtml" media-type="application/xhtml+xml"/><item id="map" href="map.png" media-type="image/png"/><item id="equation" href="equation.png" media-type="image/png"/><item id="vector" href="vector.svg" media-type="image/svg+xml"/></manifest><spine><itemref idref="one"/><itemref idref="two"/><itemref idref="three"/></spine></package>
'@
    Add-FixtureText 'EPUB/nav.xhtml' '<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Chapters</title></head><body><nav epub:type="toc"><ol><li><a href="one.xhtml">One: Anchors</a></li><li><a href="two.xhtml">Two: Images</a></li><li><a href="three.xhtml">Three: Boundary</a></li></ol></nav></body></html>'
    $repeated = 'The lantern waits beside the river. The lantern waits beside the river.'
    $paragraphs = (1..90 | ForEach-Object { "<p id=`"p$_`">Passage $_. $repeated Read slowly enough to recognize the same passage after the text reflows across a smaller screen.</p>" }) -join "`n"
    Add-FixtureText 'EPUB/one.xhtml' @"
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>One: Anchors</title></head><body><h1>One: Anchors</h1><p id="unicode">“don’t,” well-being café café 😀 lantern soft­hyphen non breaking ofﬁce.</p><p id="split">A <em>well</em>-being word across inline markup.</p><p id="duplicate-a">$repeated</p>$paragraphs<p id="duplicate-b">$repeated</p><p id="end">End of chapter one. Scrolling must stop here.</p></body></html>
"@
    Add-FixtureText 'EPUB/two.xhtml' @'
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Two: Images</title></head><body><h1>Two: Images</h1><p>Before the map lantern.</p><figure id="map-figure"><img id="map" src="map.png" alt="Map with river, north marker, and three named stations"/><figcaption id="caption">Caption lantern: the river joins North and South.</figcaption></figure><p>After the map lantern.</p><img id="ornament" src="map.png" alt="" role="presentation"/><p>After a decorative ornament.</p><figure><img id="equation" src="equation.png" alt="Energy equals mass times the speed of light squared"/><figcaption>Equation caption with selectable café.</figcaption></figure><a href="three.xhtml#end"><img id="linked" src="map.png" alt="Linked map"/></a><img id="missing-alt" src="map.png"/><img id="cover" epub:type="cover" src="map.png" alt="Reading-flow cover"/><p id="tail">End of the image chapter.</p><img id="vector" src="vector.svg" alt="Vector equation"/><picture><source srcset="equation.png" type="image/png" media="(min-width: 1px)"/><img id="responsive" src="map.png" alt="Responsive equation"/></picture><div id="background" role="img" aria-label="Meaningful background map" style="background-image:url(map.png);width:300px;height:200px"></div><svg id="inline-vector" xmlns="http://www.w3.org/2000/svg" width="1200" height="800" viewBox="0 0 1200 800" role="img" aria-label="Inline vector diagram"><rect width="1200" height="800" fill="ivory"/><circle cx="600" cy="400" r="200" fill="blue"/></svg></body></html>
'@
    $boundaryParagraphs = (1..30 | ForEach-Object { "<p>Boundary passage $_. The final destination remains original content after a deliberate image-link action.</p>" }) -join "`n"
    Add-FixtureText 'EPUB/three.xhtml' ('<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Three: Boundary</title></head><body><h1>Three: Boundary</h1><p>This is the last chapter.</p>' + $boundaryParagraphs + '<p id="end">Last original content. No wrapping.</p></body></html>')
    Add-FixtureText 'EPUB/vector.svg' '<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="800" viewBox="0 0 1200 800"><rect width="1200" height="800" fill="ivory"/><text x="200" y="400" font-size="100">E = mc²</text></svg>'
    foreach ($imageName in @('map','equation')) {
        $bitmap = [Drawing.Bitmap]::new(1200, 800)
        $graphics = [Drawing.Graphics]::FromImage($bitmap)
        $font = [Drawing.Font]::new('Arial', 36)
        $pen = [Drawing.Pen]::new([Drawing.Color]::Blue, 12)
        try {
            $graphics.Clear([Drawing.Color]::Ivory)
            if ($imageName -eq 'map') {
                $graphics.DrawLines($pen, [Drawing.Point[]]@([Drawing.Point]::new(300,0), [Drawing.Point]::new(450,300), [Drawing.Point]::new(700,500), [Drawing.Point]::new(450,800)))
                $graphics.DrawString('N ↑     NORTH     EAST     SOUTH', $font, [Drawing.Brushes]::Black, 40, 100)
                $graphics.DrawString('River map — whole image must fit', $font, [Drawing.Brushes]::Black, 40, 680)
            } else { $graphics.DrawString('E = mc²', $font, [Drawing.Brushes]::Black, 200, 300) }
            $memory = [IO.MemoryStream]::new()
            try { $bitmap.Save($memory, [Drawing.Imaging.ImageFormat]::Png); Add-FixtureEntry "EPUB/$imageName.png" $memory.ToArray() } finally { $memory.Dispose() }
        } finally { $pen.Dispose(); $font.Dispose(); $graphics.Dispose(); $bitmap.Dispose() }
    }
} finally { $fixtureZip.Dispose(); $fixtureStream.Dispose() }
Get-FileHash -LiteralPath $fixturePath -Algorithm SHA256
