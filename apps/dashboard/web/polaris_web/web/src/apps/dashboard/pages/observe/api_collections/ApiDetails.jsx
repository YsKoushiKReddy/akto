import LayoutWithTabs from "../../../components/layouts/LayoutWithTabs"
import { Box, Button, Popover, Modal, Tooltip, ActionList, VerticalStack, HorizontalStack, Tag, Text } from "@shopify/polaris"
import FlyLayout from "../../../components/layouts/FlyLayout";
import GithubCell from "../../../components/tables/cells/GithubCell";
import SampleDataList from "../../../components/shared/SampleDataList";
import { useEffect, useState } from "react";
import api from "../api";
import ApiSchema from "./ApiSchema";
import dashboardFunc from "../../transform";
import AktoGptLayout from "../../../components/aktoGpt/AktoGptLayout";
import func from "@/util/func"
import transform from "../transform";
import ApiDependency from "./ApiDependency";
import RunTest from "./RunTest";
import PersistStore from "../../../../main/PersistStore";
import values from "@/util/values";
import gptApi from "../../../components/aktoGpt/api";
import GraphMetric from '../../../components/GraphMetric'

import { HorizontalDotsMinor, FileMinor } from "@shopify/polaris-icons"
import LocalStore from "../../../../main/LocalStorageStore";
import InlineEditableText from "../../../components/shared/InlineEditableText";
import GridRows from "../../../components/shared/GridRows";
import Dropdown from "../../../components/layouts/Dropdown";

const statsOptions = [
    {label: "15 minutes", value: 15*60},
    {label: "30 minutes", value: 30*60},
    {label: "1 hour", value: 60*60},
    {label: "3 hours", value: 3*60*60},
    {label: "6 hours", value: 6*60*60},
    {label: "12 hours", value: 12*60*60},
    {label: "1 day", value: 24*60*60},
    {label: "7 days", value: 7*24*60*60}
]

function TechCard(props){
    const {cardObj} = props;
    return(
        <Tag key={cardObj.id}>
            <Text variant="bodyMd" as="span">{cardObj.name}</Text>
        </Tag> 
    )
}

function ApiDetails(props) {
    const { showDetails, setShowDetails, apiDetail, headers, getStatus, isGptActive } = props

    const localCategoryMap = LocalStore.getState().categoryMap
    const localSubCategoryMap = LocalStore.getState().subCategoryMap

    const [sampleData, setSampleData] = useState([])
    const [paramList, setParamList] = useState([])
    const [selectedUrl, setSelectedUrl] = useState({})
    const [prompts, setPrompts] = useState([])
    const [isGptScreenActive, setIsGptScreenActive] = useState(false)
    const [loading, setLoading] = useState(false)
    const [showMoreActions, setShowMoreActions] = useState(false)
    const setSelectedSampleApi = PersistStore(state => state.setSelectedSampleApi)
    const [disabledTabs, setDisabledTabs] = useState([])
    const [description, setDescription] = useState("")
    const [headersWithData, setHeadersWithData] = useState([])
    const [isEditingDescription, setIsEditingDescription] = useState(false)
    const [editableDescription, setEditableDescription] = useState(description)
    const [useLocalSubCategoryData, setUseLocalSubCategoryData] = useState(false)
    const [apiCallStats, setApiCallStats] = useState([]); 
    const [apiCallDistribution, setApiCallDistribution] = useState([]); // New state for distribution data
    const endTs = func.timeNow();
    const [startTime, setStartTime] = useState(endTs - statsOptions[0].value)

    const statusFunc = getStatus ? getStatus : (x) => {
        try {
            if (paramList && paramList.length > 0 &&
                paramList.filter(x => x?.nonSensitiveDataType).map(x => x.subTypeString).includes(x)) {
                return "info"
            }
        } catch (e) {
            return "warning"
        }
        return "warning"
    }

    const standardHeaders = new Set(transform.getStandardHeaderList())

    const getNiceBinSize = (rawSize) => {
        const magnitude = Math.pow(10, Math.floor(Math.log10(rawSize)));
        const leading = rawSize / magnitude;
        if (leading <= 1) return 1 * magnitude;
        if (leading <= 2) return 2 * magnitude;
        if (leading <= 5) return 5 * magnitude;
        return 10 * magnitude;
    };

    // Function to bin data
    const binData = (rawData, targetBins = 10) => {
        if (rawData.length === 0) return [];
    
        const sortedData = rawData.sort((a, b) => a[0] - b[0]);
        const calls = sortedData.map(point => point[0]);
    
        const minCalls = Math.max(0, Math.min(...calls));
        const maxCalls = Math.max(...calls);
        const range = maxCalls - minCalls;
    
        // ✅ Smart bin size
        const rawBinSize = Math.ceil(range / targetBins);
        const binSize = getNiceBinSize(rawBinSize);
    
        const start = Math.floor(minCalls / binSize) * binSize;
        const end = Math.ceil(maxCalls / binSize) * binSize;
    
        const bins = [];

        for (let i = start; i < end; i += binSize) {
            bins.push({ range: [i, i + binSize], count: 0 });
        }
        // for (let i = start; i <= end; i += binSize) {
        //     bins.push({ range: [i, i + binSize], count: 0 });
        // }
    
        rawData.forEach(([calls, users]) => {
            const binIndex = Math.floor((calls - start) / binSize);
            if (binIndex >= 0 && binIndex < bins.length) {
                bins[binIndex].count += users;
            }
        });
    
        return {
            data: bins.map(bin => ({
                x: (bin.range[0] + bin.range[1]) / 2,
                y: bin.count,
                binRange: [bin.range[0], bin.range[1]]
            })).filter(bin => bin.y > 0),
            binSize
        };
    };

    // Updated fetchDistributionData with binning
    const fetchDistributionData = async () => {
        try {
            const { apiCollectionId, endpoint, method } = apiDetail;
            const res = await api.fetchIpLevelApiCallStats(apiCollectionId, endpoint, method, startTime, endTs); // Use startTime
            const rawData = res?.result?.apiCallStats || [];
    
            const formattedData = rawData.map(({ count, users }) => [count, users]);
            const { data: binnedData, binSize } = binData(formattedData, 10);
    
            const chartData = [
                {
                    name: '', // Empty name since legend is disabled
                    data: binnedData,
                    color: '#1E90FF',
                    binSize
                }
            ];
    
            setApiCallDistribution(chartData);
    
            // Update disabledTabs logic to consider both stats and distribution data in ApiCallStatsTab
            setDisabledTabs(prev => {
                const newDisabledTabs = [...prev.filter(tab => tab !== "api-call-stats")];
                if (
                    (!chartData || chartData.length === 0 || !chartData[0]?.data || chartData[0].data.length === 0) &&
                    (!apiCallStats || apiCallStats.length === 0 || !apiCallStats[0]?.data || apiCallStats[0].data.length === 0)
                ) {
                    newDisabledTabs.push("api-call-stats");
                }
                return newDisabledTabs;
            });
        } catch (error) {
            console.error("Error fetching API call distribution data:", error);
            setApiCallDistribution([]);
            // Update disabledTabs logic
            setDisabledTabs(prev => {
                const newDisabledTabs = [...prev.filter(tab => tab !== "api-call-stats")];
                if (!apiCallStats || apiCallStats.length === 0 || !apiCallStats[0]?.data || apiCallStats[0].data.length === 0) {
                    newDisabledTabs.push("api-call-stats");
                }
                return newDisabledTabs;
            });
        }
    };

    const fetchStats = async (apiCollectionId, endpoint, method) => {
        try {
            setApiCallStats([]); // Clear state before fetching new data
            const res = await api.fetchApiCallStats(apiCollectionId, endpoint, method, startTime, endTs);
            const transformedData = [
                {
                    data: res.result.apiCallStats.sort((a, b) => b.ts - a.ts).map((item) => [item.ts * 60 * 1000, item.count]),
                    color: "",
                    name: 'API Calls',
                },
            ];
            setApiCallStats(transformedData);
            setDisabledTabs(prev => {
                const newDisabledTabs = [...prev.filter(tab => tab !== "api-call-stats")];
                if (!transformedData || transformedData.length === 0 || !transformedData[0]?.data || transformedData[0].data.length === 0) {
                    newDisabledTabs.push("api-call-stats");
                }
                return newDisabledTabs;
            });
        } catch (error) {
            console.error("Error fetching API call stats:", error);
            setApiCallStats([]);
            setDisabledTabs(prev => [...prev.filter(tab => tab !== "api-call-stats"), "api-call-stats"]);
        }
    };

    const fetchData = async () => {
        if (showDetails) {
            setLoading(true)
            const { apiCollectionId, endpoint, method, description } = apiDetail
            setSelectedUrl({ url: endpoint, method: method })
            api.checkIfDependencyGraphAvailable(apiCollectionId, endpoint, method).then((resp) => {
                if (!resp.dependencyGraphExists) {
                    setDisabledTabs(["dependency"])
                } else {
                    setDisabledTabs([])
                }
            })

            setTimeout(() => {
                setDescription(description == null ? "" : description)
                setEditableDescription(description == null ? "" : description)
            }, 100)
            headers.forEach((header) => {
                if (header.value === "description") {
                    header.action = () => setIsEditingDescription(true)
                }
            })

            let commonMessages = []
            await api.fetchSampleData(endpoint, apiCollectionId, method).then((res) => {
                api.fetchSensitiveSampleData(endpoint, apiCollectionId, method).then(async (resp) => {
                    if (resp.sensitiveSampleData && Object.keys(resp.sensitiveSampleData).length > 0) {
                        if (res.sampleDataList.length > 0) {
                            commonMessages = transform.getCommonSamples(res.sampleDataList[0].samples, resp)
                        } else {
                            commonMessages = transform.prepareSampleData(resp, '')
                        }
                    } else {
                        let sensitiveData = []
                        await api.loadSensitiveParameters(apiCollectionId, endpoint, method).then((res3) => {
                            sensitiveData = res3.data.endpoints;
                        })
                        let samples = res.sampleDataList.map(x => x.samples)
                        samples = samples.reverse();
                        samples = samples.flat()
                        let newResp = transform.convertSampleDataToSensitiveSampleData(samples, sensitiveData)
                        commonMessages = transform.prepareSampleData(newResp, '')
                    }
                    setSampleData(commonMessages)
                })
            })
            setTimeout(() => {
                setLoading(false)
            }, 100)
            const queryPayload = dashboardFunc.getApiPrompts(apiCollectionId, endpoint, method)[0].prepareQuery();
            try{
                if(isGptActive && window.STIGG_FEATURE_WISE_ALLOWED["AKTO_GPT_AI"] && window.STIGG_FEATURE_WISE_ALLOWED["AKTO_GPT_AI"]?.isGranted === true){
                    await gptApi.ask_ai(queryPayload).then((res) => {
                        if (res.response.responses && res.response.responses.length > 0) {
                            const metaHeaderResp = res.response.responses.filter(x => !standardHeaders.has(x.split(" ")[0]))
                            setHeadersWithData(metaHeaderResp)
                        }
                    }
                    ).catch((err) => {
                        console.error("Failed to fetch prompts:", err);
                    })
                }
            }catch (e) {
            }   
            fetchStats(apiCollectionId, endpoint, method)
            fetchDistributionData(); // Fetch distribution data
        }
    }

    const handleSaveDescription = async () => {
        const { apiCollectionId, endpoint, method } = apiDetail;
        
        setIsEditingDescription(false);
        
        if(editableDescription === description) {
            return
        }
        await api.saveEndpointDescription(apiCollectionId, endpoint, method, editableDescription)
            .then(() => {
                setDescription(editableDescription);
                func.setToast(true, false, "Description saved successfully");
            })
            .catch((err) => {
                console.error("Failed to save description:", err);
                func.setToast(true, true, "Failed to save description. Please try again.");
            });
    };

    const runTests = async (testsList) => {
        setIsGptScreenActive(false)
        const apiKeyInfo = {
            apiCollectionId: apiDetail.apiCollectionId,
            url: selectedUrl.url,
            method: selectedUrl.method
        }
        await api.scheduleTestForCustomEndpoints(apiKeyInfo, func.timNow(), false, testsList, "akto_gpt_test", -1, -1)
        func.setToast(true, false, "Triggered tests successfully!")
    }

    useEffect(() => {
        if (
            (localCategoryMap && Object.keys(localCategoryMap).length > 0) &&
            (localSubCategoryMap && Object.keys(localSubCategoryMap).length > 0)
        ) {
            setUseLocalSubCategoryData(true)
        }

        fetchData();
        setHeadersWithData([])
    }, [apiDetail])

    useEffect(() => {
        const { apiCollectionId, endpoint, method } = apiDetail;
        fetchStats(apiCollectionId, endpoint, method);
        fetchDistributionData();
        setApiCallDistribution([]);
    }, [startTime, apiDetail]);

    function displayGPT() {
        setIsGptScreenActive(true)
        let requestObj = { key: "PARAMETER", jsonStr: sampleData[0]?.message, apiCollectionId: Number(apiDetail.apiCollectionId) }
        const activePrompts = dashboardFunc.getPrompts(requestObj)
        setPrompts(activePrompts)
    }

    function isDeMergeAllowed() {
        const { endpoint } = apiDetail
        if (!endpoint || endpoint === undefined) {
            return false;
        }
        return (endpoint.includes("STRING") || endpoint.includes("INTEGER") || endpoint.includes("OBJECT_ID") || endpoint.includes("VERSIONED"))
    }

    const openTest = () => {
        const apiKeyInfo = {
            apiCollectionId: apiDetail["apiCollectionId"],
            url: selectedUrl.url,
            method: {
                "_name": selectedUrl.method
            }
        }
        setSelectedSampleApi(apiKeyInfo)
        const navUrl = window.location.origin + "/dashboard/test-editor/REMOVE_TOKENS"
        window.open(navUrl, "_blank")
    }

    const isDemergingActive = isDeMergeAllowed();

    const defaultChartOptions = (enableLegends) => {
        const options = {
          plotOptions: {
            series: {
              events: {
                legendItemClick: function () {
                  var seriesIndex = this.index;
                  var chart = this.chart;
                  var series = chart.series[seriesIndex];
    
                  chart.series.forEach(function (s) {
                    s.hide();
                  });
                  series.show();
    
                  return false;
                },
              },
            },
          },
        };
        if (enableLegends) {
          options['legend'] = { layout: 'vertical', align: 'right', verticalAlign: 'middle' };
        }
        return options;
    };

    const distributionChartOptions = {
        chart: {
            type: 'column',
            marginTop: 10,
            marginBottom: 70,
            marginRight: 10,
        },
        xAxis: {
            title: {
                text: 'API Call Frequency',
                style: {
                    fontSize: '12px',
                },
            },
            gridLineWidth: 0,
            labels: {
                style: {
                    fontSize: '12px',
                },
                enabled: true,
            },
            tickmarkPlacement: 'on',
            tickWidth: 1,
            tickLength: 5,
        },
        yAxis: {
            title: {
                text: 'Number of Users',
                style: {
                    fontSize: '12px',
                },
            },
            gridLineWidth: 0,
        },
        plotOptions: {
            column: {
                pointPadding: 0.05,
                groupPadding: 0.1,
                borderWidth: 0,
            },
        },
        tooltip: {
            formatter: function () {
                const binRange = this.point?.binRange || [Math.floor(this.x) - 15, Math.floor(this.x) + 15];
                return `<b>${this.y}</b> users made calls in range <b>${binRange[0]} to ${binRange[1] - 1}</b>`;
            },
        },
        title: { text: null },
        subtitle: { text: null },
        legend: { enabled: false },
    };

    const SchemaTab = {
        id: 'schema',
        content: "Schema",
        component:  <Box paddingBlockStart={"4"}> 
            <ApiSchema
                apiInfo={{
                    apiCollectionId: apiDetail.apiCollectionId,
                    url: apiDetail.endpoint,
                    method: apiDetail.method
                }}
            />
        </Box>
    }
    const ValuesTab = {
        id: 'values',
        content: "Values",
        component: sampleData.length > 0 && <Box paddingBlockStart={"4"}>
            <SampleDataList
                key="Sample values"
                sampleData={sampleData}
                heading={"Sample values"}
                minHeight={"35vh"}
                vertical={true}
                isAPISampleData={true}
                metadata={headersWithData.map(x => x.split(" ")[0])}
            />
        </Box>,
    }
    const DependencyTab = {
        id: 'dependency',
        content: "Dependency Graph",
        component: <Box paddingBlockStart={"2"}>
            <ApiDependency
                apiCollectionId={apiDetail['apiCollectionId']}
                endpoint={apiDetail['endpoint']}
                method={apiDetail['method']}
            />
        </Box>,
    }
    const ApiCallStatsTab = {
        id: 'api-call-stats',
        content: 'API Call Stats',
        component: 
            <Box paddingBlockStart={'4'}>
                <HorizontalStack align="end">
                    <Dropdown
                        menuItems={statsOptions}
                        initial={statsOptions[0].label}
                        selected={(timeInSeconds) => {
                            setStartTime((prev) => {
                                if ((endTs - timeInSeconds) === prev) {
                                    return prev;
                                } else {
                                    return endTs - timeInSeconds;
                                }
                            });
                        }}
                    />
                </HorizontalStack>
                <VerticalStack gap={"4"}>
                    {/* API Call Stats Graph */}
                    {apiCallStats != undefined && apiCallStats.length > 0 && apiCallStats[0]?.data !== undefined && apiCallStats[0]?.data?.length > 0 ? (
                        <GraphMetric
                            key={`stats-${startTime}`}
                            data={apiCallStats}
                            type='spline'
                            color='#6200EA'
                            areaFillHex='true'
                            height='330'
                            title='API Call Count'
                            subtitle='Number of API calls over time'
                            defaultChartOptions={defaultChartOptions(false)}
                            backgroundColor='#ffffff'
                            text='true'
                            inputMetrics={[]}
                        />
                    ) : (
                        <Box minHeight="330px" />
                    )}
                    {/* API Call Distribution Graph */}
                    {apiCallDistribution != undefined && apiCallDistribution.length > 0 && apiCallDistribution[0]?.data !== undefined && apiCallDistribution[0]?.data?.length > 0 ? (
                        <GraphMetric
                            key={`distribution-${startTime}`}
                            data={apiCallDistribution}
                            type='column'
                            color='#1E90FF'
                            height='330'
                            title={undefined}
                            subtitle={undefined}
                            defaultChartOptions={{
                                ...defaultChartOptions(false),
                                ...distributionChartOptions,
                                xAxis: {
                                    ...distributionChartOptions.xAxis,
                                    tickPositions: apiCallDistribution[0]?.data?.map(point => point.binRange[0]) || [],
                                },
                            }}
                            backgroundColor='#ffffff'
                            text={false}
                            inputMetrics={[]}
                        />
                    ) : (
                        <Box minHeight="330px" />
                    )}
                </VerticalStack>
            </Box>,
    };
    
    const deMergeApis = () => {
        api.deMergeApi(apiCollectionId, endpoint, method).then((resp) => {
            func.setToast(true, false, "De-merging successful!!.")
            window.location.reload()
        })
    }

    let newData = JSON.parse(JSON.stringify(apiDetail))
    newData['copyEndpoint'] = {
        method: apiDetail.method,
        endpoint: apiDetail.endpoint
    }

    try {
        newData['nonSensitiveTags'] = [...new Set(paramList.filter(x => x?.nonSensitiveDataType).map(x => x.subTypeString))]
    } catch (e){
    }
    try {
        newData['sensitiveTags'] = apiDetail?.sensitiveTags && apiDetail?.sensitiveTags.length > 0 ? apiDetail?.sensitiveTags : 
        [...new Set(paramList.filter(x => x?.savedAsSensitive || x?.sensitive).map(x => x.subTypeString))]
    } catch (e){
    }

    let gridData = [];
    try {
        const techValues = [...new Set(headersWithData.filter(x => x.split(" ")[1].length < 50).map(x => x.split(" ")[1]))]
        gridData = techValues.map((x) => {
            return {
                id: x,
                name: x
            }
        })
    } catch (error) {
        
    }

    newData['description'] = (isEditingDescription?<InlineEditableText textValue={editableDescription} setTextValue={setEditableDescription} handleSaveClick={handleSaveDescription} setIsEditing={setIsEditingDescription}  placeholder={"Add a brief description"} maxLength={64}/> : description )

    const headingComp = (
        <HorizontalStack align="space-between" wrap={false} key="heading">
            <VerticalStack>
                <HorizontalStack gap={"2"} wrap={false} >
                    <GithubCell
                        width="32vw"
                        data={newData}
                        headers={headers}
                        getStatus={statusFunc}
                    />
                </HorizontalStack>
            </VerticalStack>
            <VerticalStack gap="3" align="space-between">
                <HorizontalStack gap={"1"} wrap={false} >
                    <RunTest
                        apiCollectionId={apiDetail["apiCollectionId"]}
                        endpoints={[apiDetail]}
                        filtered={true}
                        useLocalSubCategoryData={useLocalSubCategoryData}
                        preActivator={false}
                        disabled={window.USER_ROLE === "GUEST"}
                    />
                    <Box>
                        <Tooltip content="Open URL in test editor" dismissOnMouseOut>
                            <Button monochrome onClick={() => openTest()} icon={FileMinor} />
                        </Tooltip>
                    </Box>
                    {
                        isGptActive || isDemergingActive ? <Popover
                            active={showMoreActions}
                            activator={
                                <Tooltip content="More actions" dismissOnMouseOut ><Button plain monochrome icon={HorizontalDotsMinor} onClick={() => setShowMoreActions(!showMoreActions)} /></Tooltip>
                            }
                            autofocusTarget="first-node"
                            onClose={() => setShowMoreActions(false)}
                        >
                            <Popover.Pane fixed>
                                <ActionList
                                    items={[
                                        isGptActive ? { content: "Ask AktoGPT", onAction: displayGPT } : {},
                                        isDemergingActive ? { content: "De-merge", onAction: deMergeApis } : {},
                                    ]}
                                />
                            </Popover.Pane>
                        </Popover> : null
                    }
                </HorizontalStack>
                {headersWithData.length > 0 && 
                    <VerticalStack gap={"1"}>
                        <Text variant="headingSm" color="subdued">Technologies used</Text>
                        <GridRows verticalGap={"2"} horizontalGap={"1"} columns={3} items={gridData.slice(0,Math.min(gridData.length ,12))} CardComponent={TechCard} />
                    </VerticalStack>
                }
            </VerticalStack>
        </HorizontalStack>
    )

    const components = [
        headingComp,
        <LayoutWithTabs
            key="tabs"
            tabs={[ValuesTab, SchemaTab, ApiCallStatsTab, DependencyTab]}
            currTab={() => { }}
            disabledTabs={disabledTabs}
        />
    ]

    return (
        <div>
            <FlyLayout
                title="API details"
                show={showDetails}
                setShow={setShowDetails}
                components={components}
                loading={loading}
            />
            <Modal large open={isGptScreenActive} onClose={() => setIsGptScreenActive(false)} title="Akto GPT">
                <Modal.Section flush>
                    <AktoGptLayout prompts={prompts} closeModal={() => setIsGptScreenActive(false)} runCustomTests={(tests) => runTests(tests)} />
                </Modal.Section>
            </Modal>
        </div>
    )
}

export default ApiDetails