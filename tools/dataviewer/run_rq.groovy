import org.openrdf.repository.sail.SailRepository
import org.openrdf.sail.memory.MemoryStore
import org.openrdf.query.QueryLanguage
import org.openrdf.rio.RDFFormat
//import org.openrdf.sail.inferencer.fc.DirectTypeHierarchyInferencer
import org.openrdf.sail.inferencer.fc.ForwardChainingRDFSInferencer


@Grab(group='org.openrdf.sesame', module='sesame', version='2.2.4')
@Grab(group='org.openrdf.sesame', module='sesame-repository-sail', version='2.2.4')
@Grab(group='org.openrdf.sesame', module='sesame-sail-memory', version='2.2.4')
@Grab(group='org.openrdf.sesame', module='sesame-queryparser-sparql', version='2.2.4')
@Grab(group='org.openrdf.sesame', module='sesame-rio-rdfxml', version='2.2.4')
@Grab(group='org.openrdf.sesame', module='sesame-rio-turtle', version='2.2.4')
@Grab(group='org.openrdf.sesame', module='sesame-rio-n3', version='2.2.4')
@Grab(group='org.slf4j', module='slf4j-api', version='1.5.0')
@Grab(group='org.slf4j', module='slf4j-jcl', version='1.5.0')
def runQuery(def conn, String query) {
    def prepQuery = conn.prepareQuery(QueryLanguage.SPARQL, query)
    prepQuery.setIncludeInferred(true)
    return prepQuery.evaluate()
}

void loadRdf(conn, sources) {
    for (source in sources) {
        def file = new File(source)
        def format = RDFFormat.forFileName(file.getName())
        conn.add(new FileInputStream(file), file.toURI().toString(), format)
    }
}

def repo = new SailRepository(new ForwardChainingRDFSInferencer(new MemoryStore()))
repo.initialize()
def conn = repo.getConnection()
loadRdf(conn, args[1..args.length-1])
def res = runQuery(conn, new File(args[0]).text)
println()
while(res.hasNext()) {
    def row = res.next()
    row.bindingNames.each {
        println "${it}: ${row.getValue(it)}"
    }
    println()
}
res.close()
conn.close()
repo.shutDown()

