import { useReducer, useState } from "react";
import DateRangeFilter from "../../components/layouts/DateRangeFilter";
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards";
import TitleWithInfo from "../../components/shared/TitleWithInfo";
import SusDataTable from "./components/SusDataTable";
import values from "@/util/values";
import { produce } from "immer"
import func from "@/util/func";
import tempFunc from "./dummyData";
import SampleDetails from "./components/SampleDetails";
function ThreatDetectionPage() {

    const [sampleData, setSampleData] = useState([])
    const initialVal = values.ranges[3]
    const [currDateRange, dispatchCurrDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), initialVal);
    const [showDetails, setShowDetails] = useState(false);
    const rowClicked = (data) => {
        const tempData = tempFunc.getSampleDataOfUrl(data.url);
        const sameRow = func.deepComparison(tempData, sampleData);
        if (!sameRow) {
            setSampleData([{"message": JSON.stringify(tempData),  "highlightPaths": []}])
            setShowDetails(true)
        } else {
            setShowDetails(!showDetails)
        }
      }

    const components = [
        <SusDataTable key={"sus-data-table"}
            currDateRange={currDateRange}
            rowClicked={rowClicked} 
        />,
        <SampleDetails
            title={"Attacker payload"}
            showDetails={showDetails}
            setShowDetails={setShowDetails}
            sampleData={sampleData}
            key={"sus-sample-details"}
        />
    ]

    return <PageWithMultipleCards
        title={
            <TitleWithInfo
                titleText={"API Threat Activity"}
                tooltipContent={"Identify malicious requests with Akto's powerful threat detection capabilities"}
            />
        }
        isFirstPage={true}
        primaryAction={<DateRangeFilter initialDispatch={currDateRange} dispatch={(dateObj) => dispatchCurrDateRange({ type: "update", period: dateObj.period, title: dateObj.title, alias: dateObj.alias })} />}
        components={components}
    />
}

export default ThreatDetectionPage;